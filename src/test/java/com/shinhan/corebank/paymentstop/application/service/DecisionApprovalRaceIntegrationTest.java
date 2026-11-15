package com.shinhan.corebank.paymentstop.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.support.CustomerTestFixture;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.paymentstop.application.port.in.AdminActor;
import com.shinhan.corebank.paymentstop.application.port.in.ApproveDecisionUseCase;
import com.shinhan.corebank.paymentstop.application.port.in.ExternalCheckUseCase;
import com.shinhan.corebank.paymentstop.application.port.in.ReceiveObjectionUseCase;
import com.shinhan.corebank.paymentstop.application.port.in.RegisterPaymentStopCaseUseCase;
import com.shinhan.corebank.paymentstop.application.port.in.ReviewProgressUseCase;
import com.shinhan.corebank.paymentstop.application.port.in.WriteDecisionUseCase;
import com.shinhan.corebank.paymentstop.domain.DecisionResult;
import com.shinhan.corebank.paymentstop.domain.ObjectionDecision;
import com.shinhan.corebank.paymentstop.domain.exception.PaymentStopErrorCode;
import com.shinhan.corebank.paymentstop.domain.external.ExternalAgency;
import com.shinhan.corebank.paymentstop.domain.external.ExternalCheckReply;
import com.shinhan.corebank.paymentstop.support.PaymentStopTestData;
import com.shinhan.corebank.paymentstop.support.PaymentStopTestData.Scenario;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 승인 직전 경쟁 조건(PR #919 리뷰 R1). 승인이 원장 사실을 계산한 뒤 이의제기 행 락을 잡기 전에 외부 확인 부정 회신이 먼저 락을 잡아
 * 커밋하는 순서를 재현한다. {@link DecisionWriter#approve} 가 락을 잡기 직전에 부정 회신을 커밋해 두면, 승인은 락 안에서 회신을
 * 다시 읽어 해제를 막아야 한다.
 */
@DisplayName("판정 승인과 외부 확인 회신 경쟁 통합 테스트")
class DecisionApprovalRaceIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private RegisterPaymentStopCaseUseCase registerUseCase;

    @Autowired
    private ReceiveObjectionUseCase receiveUseCase;

    @Autowired
    private ReviewProgressUseCase progressUseCase;

    @Autowired
    private WriteDecisionUseCase writeUseCase;

    @Autowired
    private ApproveDecisionUseCase approveUseCase;

    @Autowired
    private ExternalCheckUseCase externalCheckUseCase;

    @MockitoSpyBean
    private DecisionWriter decisionWriter;

    @Autowired
    private CustomerTestFixture customerTestFixture;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @Autowired
    private PlatformTransactionManager transactionManager;

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
    @DisplayName("승인이 게이트 입력을 읽은 뒤 락을 잡기 전에 가담 의심 회신이 커밋되면 수용을 확정하지 않고 지급정지를 유지한다")
    void negativeReplyBetweenGateAndLock_blocksRelease() {
        Scenario s = data.scenario(true);
        long objectionId = reviewing(register(s));
        ObjectionDecision pending = writeUseCase.write(
                new WriteDecisionUseCase.Command(
                        objectionId, DecisionResult.ACCEPTED, "관계 거래 확인", "관계 거래 3회", false, null),
                actor);

        // 승인 쪽이 락 밖 계산을 마친 직후, 락을 잡기 직전에 외부 확인이 먼저 락을 잡아 부정 회신을 커밋한다.
        // 승인 트랜잭션에 섞이지 않도록 다른 스레드의 별도 트랜잭션으로 커밋하고 끝날 때까지 기다린다
        ExecutorService executor = Executors.newSingleThreadExecutor();
        doAnswer(invocation -> {
                    executor.submit(() -> externalCheckUseCase.request(
                                    objectionId,
                                    ExternalAgency.POLICE,
                                    ExternalCheckReply.INVOLVEMENT_SUSPECTED,
                                    actor))
                            .get(10, TimeUnit.SECONDS);
                    return invocation.callRealMethod();
                })
                .when(decisionWriter)
                .approve(anyLong(), anyLong(), anyBoolean(), any(), any());

        try {
            assertThatThrownBy(() -> approveUseCase.approve(objectionId, pending.decisionId(), false, approver))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(PaymentStopErrorCode.RELEASE_NOT_ALLOWED);
        } finally {
            executor.shutdownNow();
        }

        assertThat(data.status(s.target())).isEqualTo("SUSPENDED");
        assertThat(data.objectionStatus(objectionId)).isEqualTo("PENDING_APPROVAL");
        assertThat(data.countAudit(approverId, "PAYMENT_STOP_RELEASE")).isZero();
        Integer replies = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM objection_external_check WHERE objection_id = ? AND reply = 'INVOLVEMENT_SUSPECTED'",
                Integer.class,
                objectionId);
        assertThat(replies).isEqualTo(1);
    }

    @Test
    @DisplayName("외부 확인이 이의제기 락을 쥔 동안 승인이 기다렸다가 락을 얻으면, 그사이 커밋된 부정 회신을 보고 수용을 막는다")
    void approvalWaitingOnLock_seesReplyCommittedMeanwhile() throws Exception {
        Scenario s = data.scenario(true);
        long objectionId = reviewing(register(s));
        ObjectionDecision pending = writeUseCase.write(
                new WriteDecisionUseCase.Command(
                        objectionId, DecisionResult.ACCEPTED, "관계 거래 확인", "관계 거래 3회", false, null),
                actor);

        CountDownLatch replyLocked = new CountDownLatch(1);
        CountDownLatch approvalAtLock = new CountDownLatch(1);
        // 승인 쪽이 락 밖 계산을 마치고 락을 잡으러 들어가는 시점을 알린다
        doAnswer(invocation -> {
                    approvalAtLock.countDown();
                    return invocation.callRealMethod();
                })
                .when(decisionWriter)
                .approve(anyLong(), anyLong(), anyBoolean(), any(), any());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            // 외부 확인 트랜잭션: 이의제기 행 락을 잡고 부정 회신을 저장한 뒤, 승인이 락 앞에서 기다리는 것을 확인하고 커밋한다
            Future<?> reply =
                    executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                        externalCheckUseCase.request(
                                objectionId, ExternalAgency.POLICE, ExternalCheckReply.INVOLVEMENT_SUSPECTED, actor);
                        replyLocked.countDown();
                        try {
                            approvalAtLock.await(10, TimeUnit.SECONDS);
                            Thread.sleep(500); // 승인 쪽 SELECT ... FOR UPDATE 가 대기에 들어갈 시간
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }));
            assertThat(replyLocked.await(10, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> approveUseCase.approve(objectionId, pending.decisionId(), false, approver))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(PaymentStopErrorCode.RELEASE_NOT_ALLOWED);
            reply.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(data.status(s.target())).isEqualTo("SUSPENDED");
        assertThat(data.objectionStatus(objectionId)).isEqualTo("PENDING_APPROVAL");
        assertThat(data.countAudit(approverId, "PAYMENT_STOP_RELEASE")).isZero();
    }

    private long register(Scenario s) {
        return registerUseCase.register(
                new RegisterPaymentStopCaseUseCase.Command(
                        s.target(), List.of(s.disputed()), LocalDate.now(clock).plusDays(30)),
                actor);
    }

    private long reviewing(long caseId) {
        long objectionId = receiveUseCase.receive(
                new ReceiveObjectionUseCase.Command(caseId, 1, "친구와 주고받은 돈입니다.", false, true, false), actor);
        progressUseCase.confirmCompleteness(objectionId, actor);
        return objectionId;
    }
}
