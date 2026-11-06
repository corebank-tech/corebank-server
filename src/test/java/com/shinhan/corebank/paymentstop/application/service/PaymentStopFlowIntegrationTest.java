package com.shinhan.corebank.paymentstop.application.service;

import static com.shinhan.corebank.paymentstop.support.PaymentStopTestData.DISPUTED_AMOUNT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.support.CustomerTestFixture;
import com.shinhan.corebank.common.audit.AccessReason;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.ErrorCode;
import com.shinhan.corebank.paymentstop.application.port.in.AdminActor;
import com.shinhan.corebank.paymentstop.application.port.in.ConfirmDecisionUseCase;
import com.shinhan.corebank.paymentstop.application.port.in.ObjectionQueryUseCase;
import com.shinhan.corebank.paymentstop.application.port.in.ReceiveObjectionUseCase;
import com.shinhan.corebank.paymentstop.application.port.in.RegisterPaymentStopCaseUseCase;
import com.shinhan.corebank.paymentstop.domain.DecisionResult;
import com.shinhan.corebank.paymentstop.domain.ObjectionDecision;
import com.shinhan.corebank.paymentstop.domain.exception.PaymentStopErrorCode;
import com.shinhan.corebank.paymentstop.domain.gate.GateBranch;
import com.shinhan.corebank.paymentstop.support.PaymentStopTestData;
import com.shinhan.corebank.paymentstop.support.PaymentStopTestData.Scenario;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** 사건 등록부터 판정 확정까지 실제 원장 데이터로 돈다. 서비스가 커밋한 결과를 JdbcTemplate 으로 다시 읽어 영속성 캐시를 피한다. */
@DisplayName("지급정지 사건, 이의제기, 판정 확정 통합 테스트")
class PaymentStopFlowIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private RegisterPaymentStopCaseUseCase registerUseCase;

    @Autowired
    private ReceiveObjectionUseCase receiveUseCase;

    @Autowired
    private ConfirmDecisionUseCase confirmUseCase;

    @Autowired
    private ObjectionQueryUseCase queryUseCase;

    @Autowired
    private CustomerTestFixture customerTestFixture;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private Clock clock;

    private Long customerId;
    private AdminActor actor;
    private PaymentStopTestData data;

    @BeforeEach
    void setUp() {
        customerId = customerTestFixture.createCustomer();
        actor = new AdminActor(customerId, "127.0.0.1");
        data = new PaymentStopTestData(
                jdbcTemplate, customerId, LocalDateTime.now(clock).minusDays(1).withNano(0));
    }

    @AfterEach
    void tearDown() {
        data.cleanup();
        customerTestFixture.deleteCustomer(customerId);
    }

    @Test
    @DisplayName("사건을 등록하면 계좌가 SUSPENDED가 되고 등록 감사가 남는다")
    void register_suspendsAccount() {
        Scenario s = data.scenario(true);

        long caseId = register(s);

        assertThat(data.status(s.target())).isEqualTo("SUSPENDED");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT sender_account_id FROM payment_stop_case WHERE case_id = ?", Long.class, caseId))
                .isEqualTo(s.sender());
        assertThat(data.auditCount("PAYMENT_STOP_APPLY")).isEqualTo(1L);
    }

    @Test
    @DisplayName("다툼 거래가 그 계좌의 입금이 아니면 등록하지 않고, 같은 거래로 두 번 등록할 수 없다")
    void register_rejectsNonDepositAndDuplicate() {
        Scenario s = data.scenario(true);
        String outgoing =
                data.transfer(s.target(), s.sender(), 10_000L, data.disputedAt().plusMinutes(5));

        assertErrorCode(
                () -> registerUseCase.register(
                        new RegisterPaymentStopCaseUseCase.Command(s.target(), List.of(outgoing), deadline()), actor),
                PaymentStopErrorCode.DISPUTED_TRANSFER_NOT_DEPOSIT);
        assertThat(data.status(s.target())).isEqualTo("ACTIVE");

        register(s);
        assertErrorCode(() -> register(s), PaymentStopErrorCode.DISPUTED_TRANSFER_ALREADY_REGISTERED);
    }

    @Test
    @DisplayName("신청 기한이 지났거나 신분증 사본 확인이 없으면 이의제기를 받지 않는다")
    void receive_rejectsDeadlineAndMissingRequirements() {
        Scenario s = data.scenario(true);
        long expiredCase = registerUseCase.register(
                new RegisterPaymentStopCaseUseCase.Command(
                        s.target(), List.of(s.disputed()), LocalDate.now(clock).minusDays(1)),
                actor);

        assertErrorCode(() -> receive(expiredCase, 1, false), PaymentStopErrorCode.OBJECTION_DEADLINE_PASSED);

        long caseId = register(data.scenario(true));
        assertErrorCode(
                () -> receiveUseCase.receive(
                        new ReceiveObjectionUseCase.Command(caseId, 1, "정상 거래입니다.", false, false, false), actor),
                PaymentStopErrorCode.OBJECTION_REQUIREMENTS_MISSING);
    }

    @Test
    @DisplayName("관계가 인정되고 사유 1호면 수용 확정으로 지급정지가 풀린다")
    void acceptWithRelationship_releasesStop() {
        Scenario s = data.scenario(true);
        long objectionId = receive(register(s), 1, false);

        ObjectionDecision decision = confirm(objectionId, DecisionResult.ACCEPTED);

        assertThat(decision.result()).isEqualTo(DecisionResult.ACCEPTED);
        assertThat(data.status(s.target())).isEqualTo("ACTIVE");
        assertThat(data.holdAmount(s.target())).isZero();
        assertThat(objectionStatus(objectionId)).isEqualTo("DECIDED");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT branch FROM gate_evaluation WHERE objection_id = ? AND purpose = 'DECISION'",
                        String.class,
                        objectionId))
                .isEqualTo("NORMAL_ACCEPT");
        assertThat(data.auditCount("OBJECTION_DECISION_CONFIRM")).isEqualTo(1L);
        assertThat(data.auditCount("PAYMENT_STOP_RELEASE")).isEqualTo(1L);
    }

    @Test
    @DisplayName("근거가 없으면 수용할 수 없고 지급정지는 그대로다(설계 불변식 1)")
    void acceptWithoutEvidence_isRejected() {
        Scenario s = data.scenario(false);
        long objectionId = receive(register(s), 1, false);

        assertErrorCode(() -> confirm(objectionId, DecisionResult.ACCEPTED), PaymentStopErrorCode.RELEASE_NOT_ALLOWED);

        assertThat(data.status(s.target())).isEqualTo("SUSPENDED");
        assertThat(objectionStatus(objectionId)).isEqualTo("RECEIVED");
    }

    @Test
    @DisplayName("사유 3호는 수용이 아니라 일부 지급정지만 열리고, 피해금만큼 보류액이 남는다(D28)")
    void clauseThree_opensPartialOnly() {
        Scenario s = data.scenario(true);
        long objectionId = receive(register(s), 3, false);

        assertErrorCode(() -> confirm(objectionId, DecisionResult.ACCEPTED), PaymentStopErrorCode.RELEASE_NOT_ALLOWED);

        ObjectionDecision decision = confirm(objectionId, DecisionResult.PARTIAL);

        assertThat(decision.requestedHoldAmount()).isEqualTo(DISPUTED_AMOUNT);
        assertThat(decision.appliedHoldAmount()).isEqualTo(DISPUTED_AMOUNT);
        assertThat(data.status(s.target())).isEqualTo("ACTIVE");
        assertThat(data.holdAmount(s.target())).isEqualTo(DISPUTED_AMOUNT);
    }

    @Test
    @DisplayName("사유 1호에서는 일부 지급정지를 확정할 수 없다(D28)")
    void clauseOne_rejectsPartial() {
        long objectionId = receive(register(data.scenario(true)), 1, false);

        assertErrorCode(() -> confirm(objectionId, DecisionResult.PARTIAL), PaymentStopErrorCode.RELEASE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("소송 계속 중이면 수용을 막고 불수용은 확정되며 지급정지가 유지된다(D31)")
    void litigationPending_blocksReleaseButAllowsReject() {
        Scenario s = data.scenario(true);
        long objectionId = receive(register(s), 1, true);

        assertErrorCode(() -> confirm(objectionId, DecisionResult.ACCEPTED), PaymentStopErrorCode.RELEASE_NOT_ALLOWED);

        ObjectionDecision rejected = confirmUseCase.confirm(
                new ConfirmDecisionUseCase.Command(objectionId, DecisionResult.REJECTED, "소송 계속", null), actor);

        assertThat(rejected.result()).isEqualTo(DecisionResult.REJECTED);
        assertThat(data.status(s.target())).isEqualTo("SUSPENDED");
        assertThat(data.auditCount("PAYMENT_STOP_RELEASE")).isZero();
    }

    @Test
    @DisplayName("수용 판정에 조기 해제 근거가 없으면 거부하고, 이미 확정된 이의제기는 다시 확정할 수 없다")
    void requiresBasisAndRejectsSecondConfirm() {
        Scenario s = data.scenario(true);
        long objectionId = receive(register(s), 1, false);

        assertErrorCode(
                () -> confirmUseCase.confirm(
                        new ConfirmDecisionUseCase.Command(objectionId, DecisionResult.ACCEPTED, "관계 확인", " "), actor),
                PaymentStopErrorCode.EARLY_RELEASE_BASIS_REQUIRED);
        assertThat(data.status(s.target())).isEqualTo("SUSPENDED");

        confirm(objectionId, DecisionResult.ACCEPTED);
        assertErrorCode(
                () -> confirm(objectionId, DecisionResult.ACCEPTED), PaymentStopErrorCode.OBJECTION_ALREADY_DECIDED);
    }

    /**
     * 테스트 트랜잭션이 이의제기 행을 먼저 잠근 채 두 확정 요청을 보내고, 둘 다 기다리는 것을 확인한 뒤 놓는다. 두 요청 모두 락 앞의
     * 상태 확인(RECEIVED)을 이미 통과했으므로, 행 락이 없으면 바로 끝나 assertBlocked 가 실패하고, 늦게 끝나더라도 두 번째 요청이
     * PST0302 가 아니라 판정 유니크 제약 위반으로 끝나 아래 단정이 실패한다.
     */
    @Test
    @DisplayName("같은 이의제기를 동시에 확정하면 하나만 확정되고 다른 하나는 PST0302다")
    void concurrentConfirm_onlyOneSucceeds() throws Exception {
        Scenario s = data.scenario(true);
        long objectionId = receive(register(s), 1, false);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<ObjectionDecision>> futures = new ArrayList<>();
        try {
            transactionTemplate.executeWithoutResult(status -> {
                jdbcTemplate.queryForObject(
                        "SELECT objection_id FROM objection WHERE objection_id = ? FOR UPDATE",
                        Long.class,
                        objectionId);
                for (int i = 0; i < 2; i++) {
                    futures.add(pool.submit(() -> confirm(objectionId, DecisionResult.ACCEPTED)));
                }
                // 두 요청이 이의제기 행 락에서 기다리는지 본다(BusinessDateGateIntegrationTest 와 같은 방식)
                futures.forEach(PaymentStopFlowIntegrationTest::assertBlocked);
            });

            int succeeded = 0;
            List<Throwable> failures = new ArrayList<>();
            for (Future<ObjectionDecision> future : futures) {
                try {
                    future.get();
                    succeeded++;
                } catch (ExecutionException e) {
                    failures.add(e.getCause());
                }
            }

            assertThat(succeeded).isEqualTo(1);
            assertThat(failures)
                    .singleElement()
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(PaymentStopErrorCode.OBJECTION_ALREADY_DECIDED);
            assertThat(data.auditCount("PAYMENT_STOP_RELEASE")).isEqualTo(1L);
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM objection_decision WHERE objection_id = ?", Long.class, objectionId))
                    .isEqualTo(1L);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("상세는 해제 요건 점검과 열린 판정을 담고 조회 사유와 함께 조회 감사를 남기며, 목록에도 나온다")
    void detail_includesGateAndRecordsAccessAudit() {
        long objectionId = receive(register(data.scenario(true)), 1, false);

        ObjectionQueryUseCase.Detail detail = queryUseCase.detail(objectionId, actor, AccessReason.OPERATION);

        assertThat(detail.gate().branch()).isEqualTo(GateBranch.NORMAL_ACCEPT);
        assertThat(detail.gate().allowsRelease(DecisionResult.ACCEPTED)).isTrue();
        assertThat(detail.decision()).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT access_reason FROM audit_log WHERE customer_id = ? AND event_type = 'OBJECTION_DETAIL_VIEW'",
                        String.class,
                        customerId))
                .isEqualTo("OPERATION");
        assertThat(queryUseCase.list()).anySatisfy(summary -> {
            assertThat(summary.objection().getObjectionId()).isEqualTo(objectionId);
            assertThat(summary.paymentStopCase().disputedTotal()).isEqualTo(DISPUTED_AMOUNT);
        });
    }

    private static void assertBlocked(Future<?> future) {
        assertThatThrownBy(() -> future.get(700, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
    }

    private long register(Scenario s) {
        return registerUseCase.register(
                new RegisterPaymentStopCaseUseCase.Command(s.target(), List.of(s.disputed()), deadline()), actor);
    }

    private long receive(long caseId, int clause, boolean litigationPending) {
        return receiveUseCase.receive(
                new ReceiveObjectionUseCase.Command(caseId, clause, "판매 대금으로 받은 돈입니다.", false, true, litigationPending),
                actor);
    }

    private ObjectionDecision confirm(long objectionId, DecisionResult result) {
        return confirmUseCase.confirm(
                new ConfirmDecisionUseCase.Command(objectionId, result, "관계 거래 확인", "관계 거래 원장 확인"), actor);
    }

    private String objectionStatus(long objectionId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM objection WHERE objection_id = ?", String.class, objectionId);
    }

    private LocalDate deadline() {
        return LocalDate.now(clock).plusDays(30);
    }

    private void assertErrorCode(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
    }
}
