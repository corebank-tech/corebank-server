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
import com.shinhan.corebank.paymentstop.application.port.in.ApproveDecisionUseCase;
import com.shinhan.corebank.paymentstop.application.port.in.ExternalCheckUseCase;
import com.shinhan.corebank.paymentstop.application.port.in.ObjectionQueryUseCase;
import com.shinhan.corebank.paymentstop.application.port.in.ReceiveObjectionUseCase;
import com.shinhan.corebank.paymentstop.application.port.in.RegisterPaymentStopCaseUseCase;
import com.shinhan.corebank.paymentstop.application.port.in.ReviewProgressUseCase;
import com.shinhan.corebank.paymentstop.application.port.in.WriteDecisionUseCase;
import com.shinhan.corebank.paymentstop.domain.DecisionResult;
import com.shinhan.corebank.paymentstop.domain.DecisionStatus;
import com.shinhan.corebank.paymentstop.domain.ObjectionDecision;
import com.shinhan.corebank.paymentstop.domain.exception.PaymentStopErrorCode;
import com.shinhan.corebank.paymentstop.domain.external.ExternalAgency;
import com.shinhan.corebank.paymentstop.domain.external.ExternalCheck;
import com.shinhan.corebank.paymentstop.domain.external.ExternalCheckReply;
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
import org.springframework.dao.DataAccessException;
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
    private WriteDecisionUseCase writeUseCase;

    @Autowired
    private ApproveDecisionUseCase approveUseCase;

    @Autowired
    private ReviewProgressUseCase progressUseCase;

    @Autowired
    private ExternalCheckUseCase externalCheckUseCase;

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
    private Long approverId;
    private AdminActor actor;
    private AdminActor approver;
    private PaymentStopTestData data;

    @BeforeEach
    void setUp() {
        customerId = customerTestFixture.createCustomer();
        approverId = customerTestFixture.createCustomer();
        actor = new AdminActor(customerId, "127.0.0.1");
        approver = new AdminActor(approverId, "127.0.0.1");
        data = new PaymentStopTestData(
                jdbcTemplate, customerId, LocalDateTime.now(clock).minusDays(1).withNano(0));
    }

    @AfterEach
    void tearDown() {
        data.cleanup();
        data.cleanupActor(approverId);
        customerTestFixture.deleteCustomer(customerId);
        customerTestFixture.deleteCustomer(approverId);
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
    @DisplayName("수용은 작성만으로는 계좌를 건드리지 않고 승인 대기가 되며, 다른 관리자가 승인하면 지급정지가 풀리고 통지가 남는다")
    void acceptRequiresApprovalThenReleases() {
        Scenario s = data.scenario(true);
        long objectionId = reviewing(register(s), 1, false);

        ObjectionDecision pending = write(objectionId, DecisionResult.ACCEPTED);

        assertThat(pending.status()).isEqualTo(DecisionStatus.PENDING_APPROVAL);
        assertThat(data.status(s.target())).isEqualTo("SUSPENDED");
        assertThat(data.objectionStatus(objectionId)).isEqualTo("PENDING_APPROVAL");
        assertThat(data.auditCount("OBJECTION_DECISION_SUBMIT")).isEqualTo(1L);
        assertThat(data.auditCount("PAYMENT_STOP_RELEASE")).isZero();
        assertThat(data.notices(objectionId)).containsExactly("HOLDER:RECEIPT");

        ObjectionDecision confirmed = approveUseCase.approve(objectionId, pending.decisionId(), approver);

        assertThat(confirmed.status()).isEqualTo(DecisionStatus.CONFIRMED);
        assertThat(confirmed.approvedBy()).isEqualTo(approverId);
        assertThat(data.status(s.target())).isEqualTo("ACTIVE");
        assertThat(data.holdAmount(s.target())).isZero();
        assertThat(data.objectionStatus(objectionId)).isEqualTo("DECIDED");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT branch FROM gate_evaluation WHERE objection_id = ? AND purpose = 'DECISION'",
                        String.class,
                        objectionId))
                .isEqualTo("NORMAL_ACCEPT");
        assertThat(data.countAudit(approverId, "OBJECTION_DECISION_CONFIRM")).isEqualTo(1L);
        assertThat(data.countAudit(approverId, "PAYMENT_STOP_RELEASE")).isEqualTo(1L);
        // 수용은 피해자와 금감원, 전부 해제라 지급정지 종료 통지(명의인, 피해자, 관련 금융회사)까지 남는다(D30)
        assertThat(data.notices(objectionId))
                .containsExactly(
                        "HOLDER:RECEIPT",
                        "VICTIM:DECISION_ACCEPTED",
                        "FSS:DECISION_ACCEPTED",
                        "HOLDER:STOP_TERMINATED",
                        "VICTIM:STOP_TERMINATED",
                        "RELATED_BANK:STOP_TERMINATED");
    }

    @Test
    @DisplayName("작성자는 자기 판정을 승인할 수 없고(PST0309) 계좌는 그대로다")
    void authorCannotApprove() {
        Scenario s = data.scenario(true);
        long objectionId = reviewing(register(s), 1, false);
        ObjectionDecision pending = write(objectionId, DecisionResult.ACCEPTED);

        assertErrorCode(
                () -> approveUseCase.approve(objectionId, pending.decisionId(), actor),
                PaymentStopErrorCode.APPROVER_SAME_AS_AUTHOR);

        assertThat(data.status(s.target())).isEqualTo("SUSPENDED");
        assertThat(data.objectionStatus(objectionId)).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    @DisplayName("승인자가 반려하면 판정은 RETURNED, 이의제기는 심사 중으로 돌아가고 다시 써서 승인할 수 있다")
    void returnThenRewrite() {
        Scenario s = data.scenario(true);
        long objectionId = reviewing(register(s), 1, false);
        ObjectionDecision first = write(objectionId, DecisionResult.ACCEPTED);

        ObjectionDecision returned =
                approveUseCase.returnDecision(objectionId, first.decisionId(), "근거 보강 필요", approver);

        assertThat(returned.status()).isEqualTo(DecisionStatus.RETURNED);
        assertThat(returned.returnReason()).isEqualTo("근거 보강 필요");
        assertThat(data.objectionStatus(objectionId)).isEqualTo("UNDER_REVIEW");
        assertThat(data.status(s.target())).isEqualTo("SUSPENDED");
        assertErrorCode(
                () -> approveUseCase.approve(objectionId, first.decisionId(), approver),
                PaymentStopErrorCode.PENDING_DECISION_NOT_FOUND);

        ObjectionDecision second = write(objectionId, DecisionResult.ACCEPTED);
        approveUseCase.approve(objectionId, second.decisionId(), approver);

        assertThat(data.status(s.target())).isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForList(
                        "SELECT status FROM objection_decision WHERE objection_id = ? ORDER BY decision_id",
                        String.class,
                        objectionId))
                .containsExactly("RETURNED", "CONFIRMED");
    }

    @Test
    @DisplayName("수용은 소명 완비 확인 전에는 쓸 수 없고(PST0308), 불수용은 접수 상태에서도 바로 확정된다")
    void acceptNeedsCompletenessButRejectDoesNot() {
        Scenario s = data.scenario(true);
        long objectionId = receive(register(s), 1, false);

        assertErrorCode(
                () -> write(objectionId, DecisionResult.ACCEPTED), PaymentStopErrorCode.INVALID_OBJECTION_STATE);

        ObjectionDecision rejected = writeUseCase.write(
                new WriteDecisionUseCase.Command(objectionId, DecisionResult.REJECTED, "소명 부족", null), actor);

        assertThat(rejected.status()).isEqualTo(DecisionStatus.CONFIRMED);
        assertThat(data.objectionStatus(objectionId)).isEqualTo("DECIDED");
        assertThat(data.notices(objectionId)).containsExactly("HOLDER:RECEIPT", "HOLDER:DECISION_REJECTED");
    }

    @Test
    @DisplayName("근거가 없으면 수용을 쓸 수 없고 지급정지는 그대로다(설계 불변식 1)")
    void acceptWithoutEvidence_isRejected() {
        Scenario s = data.scenario(false);
        long objectionId = reviewing(register(s), 1, false);

        assertErrorCode(() -> write(objectionId, DecisionResult.ACCEPTED), PaymentStopErrorCode.RELEASE_NOT_ALLOWED);

        assertThat(data.status(s.target())).isEqualTo("SUSPENDED");
        assertThat(data.objectionStatus(objectionId)).isEqualTo("UNDER_REVIEW");
    }

    @Test
    @DisplayName("사유 3호는 일부 지급정지만 열리고, 승인하면 피해금만큼 보류액이 남고 피해자와 금감원 통지가 남는다(D28)")
    void clauseThree_opensPartialOnly() {
        Scenario s = data.scenario(true);
        long objectionId = reviewing(register(s), 3, false);

        assertErrorCode(() -> write(objectionId, DecisionResult.ACCEPTED), PaymentStopErrorCode.RELEASE_NOT_ALLOWED);

        ObjectionDecision pending = write(objectionId, DecisionResult.PARTIAL);
        assertThat(pending.requestedHoldAmount()).isNull();
        assertThat(data.holdAmount(s.target())).isZero();

        ObjectionDecision decision = approveUseCase.approve(objectionId, pending.decisionId(), approver);

        assertThat(decision.requestedHoldAmount()).isEqualTo(DISPUTED_AMOUNT);
        assertThat(decision.appliedHoldAmount()).isEqualTo(DISPUTED_AMOUNT);
        assertThat(data.status(s.target())).isEqualTo("ACTIVE");
        assertThat(data.holdAmount(s.target())).isEqualTo(DISPUTED_AMOUNT);
        assertThat(data.notices(objectionId))
                .containsExactly("HOLDER:RECEIPT", "VICTIM:DECISION_PARTIAL", "FSS:DECISION_PARTIAL");
        // 확정된 일부 지급정지에서 보류액이 빠지면 DB CHECK 가 막는다. NULL 비교가 UNKNOWN 으로 통과하지 않아야 한다
        assertThatThrownBy(() -> jdbcTemplate.update(
                        "UPDATE objection_decision SET requested_hold_amount = NULL WHERE decision_id = ?",
                        decision.decisionId()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_objection_decision_hold");
    }

    @Test
    @DisplayName("사유 1호에서는 일부 지급정지를 쓸 수 없다(D28)")
    void clauseOne_rejectsPartial() {
        long objectionId = reviewing(register(data.scenario(true)), 1, false);

        assertErrorCode(() -> write(objectionId, DecisionResult.PARTIAL), PaymentStopErrorCode.RELEASE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("소송 계속 중이면 수용을 막고 불수용은 확정되며 지급정지가 유지된다(D31)")
    void litigationPending_blocksReleaseButAllowsReject() {
        Scenario s = data.scenario(true);
        long objectionId = reviewing(register(s), 1, true);

        assertErrorCode(() -> write(objectionId, DecisionResult.ACCEPTED), PaymentStopErrorCode.RELEASE_NOT_ALLOWED);

        ObjectionDecision rejected = writeUseCase.write(
                new WriteDecisionUseCase.Command(objectionId, DecisionResult.REJECTED, "소송 계속", null), actor);

        assertThat(rejected.result()).isEqualTo(DecisionResult.REJECTED);
        assertThat(data.status(s.target())).isEqualTo("SUSPENDED");
        assertThat(data.auditCount("PAYMENT_STOP_RELEASE")).isZero();
    }

    @Test
    @DisplayName("수용 판정에 조기 해제 근거가 없으면 거부하고, 확정된 이의제기에는 다시 쓸 수 없다")
    void requiresBasisAndRejectsSecondDecision() {
        Scenario s = data.scenario(true);
        long objectionId = reviewing(register(s), 1, false);

        assertErrorCode(
                () -> writeUseCase.write(
                        new WriteDecisionUseCase.Command(objectionId, DecisionResult.ACCEPTED, "관계 확인", " "), actor),
                PaymentStopErrorCode.EARLY_RELEASE_BASIS_REQUIRED);
        assertThat(data.status(s.target())).isEqualTo("SUSPENDED");

        ObjectionDecision pending = write(objectionId, DecisionResult.ACCEPTED);
        assertErrorCode(
                () -> write(objectionId, DecisionResult.ACCEPTED), PaymentStopErrorCode.INVALID_OBJECTION_STATE);
        approveUseCase.approve(objectionId, pending.decisionId(), approver);
        assertErrorCode(
                () -> write(objectionId, DecisionResult.REJECTED), PaymentStopErrorCode.OBJECTION_ALREADY_DECIDED);
    }

    @Test
    @DisplayName("수사기관 가담 의심 회신이 오면 관계가 있어도 게이트가 FRAUD_PATTERN 이 되고 승인 대기 수용도 승인되지 않는다(D11)")
    void negativeExternalReplyBlocksApproval() {
        Scenario s = data.scenario(true);
        long objectionId = reviewing(register(s), 1, false);
        ObjectionDecision pending = write(objectionId, DecisionResult.ACCEPTED);

        externalCheckUseCase.request(
                objectionId, ExternalAgency.POLICE, ExternalCheckReply.INVOLVEMENT_SUSPECTED, actor);

        assertThat(queryUseCase
                        .detail(objectionId, actor, AccessReason.OPERATION)
                        .gate()
                        .branch())
                .isEqualTo(GateBranch.FRAUD_PATTERN);
        assertErrorCode(
                () -> approveUseCase.approve(objectionId, pending.decisionId(), approver),
                PaymentStopErrorCode.RELEASE_NOT_ALLOWED);
        assertThat(data.status(s.target())).isEqualTo("SUSPENDED");
        assertThat(data.objectionStatus(objectionId)).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    @DisplayName("관계 근거가 없어도 명의인 무관 회신이면 수용이 열리고, 해당 없음 회신은 게이트를 바꾸지 않는다")
    void ownerUnrelatedReplyOpensAccept() {
        Scenario s = data.scenario(false);
        long objectionId = reviewing(register(s), 1, false);

        externalCheckUseCase.request(objectionId, ExternalAgency.PEER_BANK, ExternalCheckReply.NO_RECORD, actor);
        assertThat(queryUseCase
                        .detail(objectionId, actor, AccessReason.OPERATION)
                        .gate()
                        .branch())
                .isEqualTo(GateBranch.UNDETERMINED);

        externalCheckUseCase.request(objectionId, ExternalAgency.POLICE, ExternalCheckReply.OWNER_UNRELATED, actor);
        assertThat(queryUseCase
                        .detail(objectionId, actor, AccessReason.OPERATION)
                        .gate()
                        .branch())
                .isEqualTo(GateBranch.NORMAL_ACCEPT);

        ObjectionDecision pending = write(objectionId, DecisionResult.ACCEPTED);
        approveUseCase.approve(objectionId, pending.decisionId(), approver);
        assertThat(data.status(s.target())).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("같은 기관에 다시 요청하면 새 행 없이 처음 회신을 그대로 돌려준다")
    void duplicateExternalCheckIsIdempotent() {
        long objectionId = receive(register(data.scenario(true)), 1, false);

        ExternalCheck first =
                externalCheckUseCase.request(objectionId, ExternalAgency.POLICE, ExternalCheckReply.NO_RECORD, actor);
        ExternalCheck again = externalCheckUseCase.request(
                objectionId, ExternalAgency.POLICE, ExternalCheckReply.INVOLVEMENT_SUSPECTED, actor);

        assertThat(again.checkId()).isEqualTo(first.checkId());
        assertThat(again.reply()).isEqualTo(ExternalCheckReply.NO_RECORD);
        assertThat(externalCheckUseCase.list(objectionId)).hasSize(1);
    }

    /**
     * 테스트 트랜잭션이 이의제기 행을 먼저 잠근 채 두 승인 요청을 보내고, 둘 다 기다리는 것을 확인한 뒤 놓는다. 두 요청 모두 락 앞의
     * 확인(승인 대기, 작성자와 다름)을 이미 통과했으므로, 행 락이 없으면 바로 끝나 assertBlocked 가 실패하고, 늦게 끝나더라도 두 번째
     * 요청이 PST0302 가 아니라 계좌 해제를 두 번 해 아래 단정이 실패한다.
     */
    @Test
    @DisplayName("같은 판정을 동시에 승인하면 하나만 확정되고 다른 하나는 PST0302다")
    void concurrentApprove_onlyOneSucceeds() throws Exception {
        Scenario s = data.scenario(true);
        long objectionId = reviewing(register(s), 1, false);
        long decisionId = write(objectionId, DecisionResult.ACCEPTED).decisionId();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<ObjectionDecision>> futures = new ArrayList<>();
        try {
            transactionTemplate.executeWithoutResult(status -> {
                jdbcTemplate.queryForObject(
                        "SELECT objection_id FROM objection WHERE objection_id = ? FOR UPDATE",
                        Long.class,
                        objectionId);
                for (int i = 0; i < 2; i++) {
                    futures.add(pool.submit(() -> approveUseCase.approve(objectionId, decisionId, approver)));
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
            assertThat(data.countAudit(approverId, "PAYMENT_STOP_RELEASE")).isEqualTo(1L);
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM objection_decision WHERE objection_id = ?", Long.class, objectionId))
                    .isEqualTo(1L);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("보완 요청은 접수 상태에서도 되고 심사 기한을 멈추며, 보완을 기다리는 중에는 다시 요청하거나 완비를 확인할 수 없다")
    void supplementRequestStopsReviewClock() {
        long objectionId = reviewing(register(data.scenario(true)), 1, false);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT review_deadline FROM objection WHERE objection_id = ?", LocalDate.class, objectionId))
                .isNotNull();

        ReviewProgressUseCase.SupplementResult result =
                progressUseCase.requestSupplement(objectionId, "계약서 원본 필요", List.of("물품 계약서"), actor);

        assertThat(result.round()).isEqualTo(1);
        assertThat(result.supplementDue()).isAfter(LocalDate.now(clock));
        assertThat(data.objectionStatus(objectionId)).isEqualTo("SUPPLEMENT_REQUESTED");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT review_deadline FROM objection WHERE objection_id = ?", LocalDate.class, objectionId))
                .isNull();
        assertThat(data.notices(objectionId)).containsExactly("HOLDER:RECEIPT", "HOLDER:SUPPLEMENT_REQUEST");
        assertErrorCode(
                () -> progressUseCase.requestSupplement(objectionId, "다시", List.of(), actor),
                PaymentStopErrorCode.INVALID_OBJECTION_STATE);
        assertErrorCode(
                () -> progressUseCase.confirmCompleteness(objectionId, actor),
                PaymentStopErrorCode.INVALID_OBJECTION_STATE);
        assertErrorCode(
                () -> write(objectionId, DecisionResult.ACCEPTED), PaymentStopErrorCode.INVALID_OBJECTION_STATE);
    }

    @Test
    @DisplayName("상세는 해제 요건 점검, 기한, 승인 대기 판정을 담고 조회 사유와 함께 조회 감사를 남기며, 목록에도 나온다")
    void detail_includesGateAndRecordsAccessAudit() {
        long objectionId = reviewing(register(data.scenario(true)), 1, false);
        ObjectionDecision pending = write(objectionId, DecisionResult.ACCEPTED);

        ObjectionQueryUseCase.Detail detail = queryUseCase.detail(objectionId, actor, AccessReason.OPERATION);

        assertThat(detail.gate().branch()).isEqualTo(GateBranch.NORMAL_ACCEPT);
        assertThat(detail.gate().allowsRelease(DecisionResult.ACCEPTED)).isTrue();
        assertThat(detail.decision()).isEmpty();
        assertThat(detail.pendingDecision())
                .hasValueSatisfying(d -> assertThat(d.decisionId()).isEqualTo(pending.decisionId()));
        assertThat(detail.objection().getReviewDeadline()).isAfter(LocalDate.now(clock));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT access_reason FROM audit_log WHERE customer_id = ? AND event_type = 'OBJECTION_DETAIL_VIEW'",
                        String.class,
                        customerId))
                .isEqualTo("OPERATION");
        assertThat(queryUseCase.list()).anySatisfy(summary -> {
            assertThat(summary.objection().getObjectionId()).isEqualTo(objectionId);
            assertThat(summary.paymentStopCase().disputedTotal()).isEqualTo(DISPUTED_AMOUNT);
            assertThat(summary.latestRunStatus()).isNotNull();
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

    // 접수하고 소명 완비까지 확인한 이의제기. 수용과 일부 지급정지는 이 상태에서만 쓸 수 있다
    private long reviewing(long caseId, int clause, boolean litigationPending) {
        long objectionId = receive(caseId, clause, litigationPending);
        progressUseCase.confirmCompleteness(objectionId, actor);
        return objectionId;
    }

    private ObjectionDecision write(long objectionId, DecisionResult result) {
        return writeUseCase.write(
                new WriteDecisionUseCase.Command(objectionId, result, "관계 거래 확인", "관계 거래 원장 확인"), actor);
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
