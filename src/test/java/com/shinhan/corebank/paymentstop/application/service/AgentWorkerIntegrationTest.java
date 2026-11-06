package com.shinhan.corebank.paymentstop.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.support.CustomerTestFixture;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.common.exception.ErrorCode;
import com.shinhan.corebank.paymentstop.application.port.in.AdminActor;
import com.shinhan.corebank.paymentstop.application.port.in.AgentRunQueryUseCase;
import com.shinhan.corebank.paymentstop.application.port.in.AgentWorkerUseCase;
import com.shinhan.corebank.paymentstop.application.port.in.AgentWorkerUseCase.ClaimedJob;
import com.shinhan.corebank.paymentstop.application.port.in.AgentWorkerUseCase.DraftCommand;
import com.shinhan.corebank.paymentstop.application.port.in.AgentWorkerUseCase.DraftResult;
import com.shinhan.corebank.paymentstop.application.port.in.AgentWorkerUseCase.LedgerItem;
import com.shinhan.corebank.paymentstop.application.port.in.AgentWorkerUseCase.StepCommand;
import com.shinhan.corebank.paymentstop.application.port.in.ReceiveObjectionUseCase;
import com.shinhan.corebank.paymentstop.application.port.in.RegisterPaymentStopCaseUseCase;
import com.shinhan.corebank.paymentstop.application.port.in.RequestReviewUseCase;
import com.shinhan.corebank.paymentstop.domain.agent.AgentRunStep;
import com.shinhan.corebank.paymentstop.domain.agent.AgentStepStatus;
import com.shinhan.corebank.paymentstop.domain.agent.AgentStepType;
import com.shinhan.corebank.paymentstop.domain.exception.PaymentStopErrorCode;
import com.shinhan.corebank.paymentstop.domain.gate.GateBranch;
import com.shinhan.corebank.paymentstop.support.PaymentStopTestData;
import com.shinhan.corebank.paymentstop.support.PaymentStopTestData.Scenario;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

/** Agent 워커 내부 API 의 인증, 대기열, 단계 이벤트, 판정안 제출. 실제 원장 데이터와 서버 게이트를 탄다. */
@DisplayName("이의제기 Agent 워커 내부 API 통합 테스트")
class AgentWorkerIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private AgentWorkerUseCase worker;

    @Autowired
    private AgentRunQueryUseCase runQuery;

    @Autowired
    private RequestReviewUseCase requestReview;

    @Autowired
    private RegisterPaymentStopCaseUseCase registerUseCase;

    @Autowired
    private ReceiveObjectionUseCase receiveUseCase;

    @Autowired
    private JobTokenService jobTokenService;

    @Autowired
    private CustomerTestFixture customerTestFixture;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @Value("${app.payment-stop.agent.worker-key}")
    private String workerKey;

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
    @DisplayName("이의제기를 접수하면 분석이 대기열에 들어가고, 워커가 가져가면 시도 1과 다음 순번 1을 받는다")
    void receiveEnqueuesAndClaimTakesIt() {
        long objectionId = receiveObjection(data.scenario(true));

        assertThat(runStatus(objectionId)).isEqualTo("QUEUED");

        ClaimedJob job = claimOwn(objectionId);
        assertThat(job.attempt()).isEqualTo(1);
        assertThat(job.nextSeq()).isEqualTo(1);
        assertThat(runStatus(objectionId)).isEqualTo("RUNNING");
        assertThat(worker.claim(workerKey)).isEmpty();
    }

    @Test
    @DisplayName("워커 키가 없거나 틀리면 작업을 가져갈 수 없다(PST0101)")
    void claimRequiresWorkerKey() {
        receiveObjection(data.scenario(true));

        assertErrorCode(() -> worker.claim(null), PaymentStopErrorCode.WORKER_AUTH_FAILED);
        assertErrorCode(() -> worker.claim("wrong-key"), PaymentStopErrorCode.WORKER_AUTH_FAILED);
    }

    @Test
    @DisplayName("두 워커가 동시에 가져가도 한 실행은 한 워커에게만 간다")
    void concurrentClaimGivesOneJob() throws Exception {
        long objectionId = receiveObjection(data.scenario(true));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Optional<ClaimedJob>>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return worker.claim(workerKey);
                }));
            }
            start.countDown();
            long claimed = 0;
            for (Future<Optional<ClaimedJob>> future : futures) {
                if (future.get(10, TimeUnit.SECONDS)
                        .filter(job -> job.objectionId() == objectionId)
                        .isPresent()) {
                    claimed++;
                }
            }
            assertThat(claimed).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT attempt FROM agent_run WHERE objection_id = ?", Integer.class, objectionId))
                    .isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("점유가 만료되면 다시 가져갈 수 있고 앞선 시도의 토큰은 거부된다. 세 번째 시도까지 만료되면 FAILED다")
    void leaseExpiryRequeuesAndExhaustsAfterThreeAttempts() {
        long objectionId = receiveObjection(data.scenario(true));
        ClaimedJob first = claimOwn(objectionId);

        expireLease(objectionId);
        ClaimedJob second = claimOwn(objectionId);

        assertThat(second.attempt()).isEqualTo(2);
        // 앞선 시도가 남긴 단계가 없으니 서버가 남긴 FAILED 가 1번이고 새 시도는 2번부터 보낸다
        assertThat(second.nextSeq()).isEqualTo(2);
        assertThat(runQuery.steps(objectionId, second.runId(), 0))
                .singleElement()
                .satisfies(step -> {
                    assertThat(step.type()).isEqualTo(AgentStepType.FAILED);
                    assertThat(step.detail()).containsEntry("by", "SERVER");
                });
        assertErrorCode(() -> worker.context(first.jobToken(), objectionId), PaymentStopErrorCode.JOB_TOKEN_INVALID);
        assertThat(worker.context(second.jobToken(), objectionId).objectionId()).isEqualTo(objectionId);

        expireLease(objectionId);
        claimOwn(objectionId);
        expireLease(objectionId);

        assertThat(worker.claim(workerKey).filter(job -> job.objectionId() == objectionId))
                .isEmpty();
        assertThat(runStatus(objectionId)).isEqualTo("FAILED");
        assertThat(runQuery.steps(objectionId, second.runId(), 0))
                .extracting(AgentRunStep::type)
                .containsExactly(AgentStepType.FAILED, AgentStepType.FAILED, AgentStepType.FAILED);
    }

    @Test
    @DisplayName("작업 토큰은 그 이의제기에만 쓰이고, 만료된 토큰은 거부된다(PST0102)")
    void jobTokenIsScopedAndExpires() {
        long objectionId = receiveObjection(data.scenario(true));
        long otherObjectionId = receiveObjection(data.scenario(true));
        ClaimedJob job = claimOwn(objectionId);

        assertErrorCode(() -> worker.context(job.jobToken(), otherObjectionId), PaymentStopErrorCode.JOB_TOKEN_INVALID);

        String expired = jobTokenService.issue(
                job.runId(),
                objectionId,
                job.attempt(),
                LocalDateTime.now(clock).minusHours(1));
        assertErrorCode(() -> worker.context(expired, objectionId), PaymentStopErrorCode.JOB_TOKEN_INVALID);
    }

    @Test
    @DisplayName("사건 정보의 소명은 가명 처리되고, 원장은 게이트 기간 안만 상대 가명 키로 준다")
    void contextAndLedgerArePseudonymizedAndBounded() {
        Scenario s = data.scenario(true);
        long caseId = register(s);
        long objectionId = receiveUseCase.receive(
                new ReceiveObjectionUseCase.Command(
                        caseId, 1, "연락처 010-1234-5678 로 확인해 주세요. 판매 대금입니다.", false, true, false),
                actor);
        ClaimedJob job = claimOwn(objectionId);

        var context = worker.context(job.jobToken(), objectionId);
        assertThat(context.statement()).contains("[연락처]").doesNotContain("010-1234-5678");
        assertThat(context.disputedTransfers()).singleElement().satisfies(item -> {
            assertThat(item.transactionNumber()).isEqualTo(s.disputed());
            assertThat(item.counterpartyKey()).matches("CP-[0-9A-F]{8}");
        });

        List<LedgerItem> all = worker.ledger(job.jobToken(), objectionId, null, null, null);
        assertThat(all).hasSize(4);
        String senderKey = context.disputedTransfers().getFirst().counterpartyKey();
        assertThat(worker.ledger(job.jobToken(), objectionId, null, null, senderKey))
                .hasSize(4);
        assertThat(worker.ledger(job.jobToken(), objectionId, null, null, "CP-00000000"))
                .isEmpty();
        // 게이트 기간 밖을 요청해도 기간 안으로 줄인다
        assertThat(worker.ledger(
                        job.jobToken(),
                        objectionId,
                        LocalDateTime.now(clock).minusYears(5),
                        data.disputedAt().minusDays(10),
                        null))
                .hasSize(2);
    }

    @Test
    @DisplayName("단계는 순번대로만 받고 같은 순번 재전송은 무시하며, 원문 개인정보를 가려 저장한다")
    void stepsAreOrderedIdempotentAndMasked() {
        long objectionId = receiveObjection(data.scenario(true));
        ClaimedJob job = claimOwn(objectionId);

        worker.recordStep(job.jobToken(), job.runId(), step(1, AgentStepType.CLAIMED, "작업 시작"));
        worker.recordStep(job.jobToken(), job.runId(), step(1, AgentStepType.CLAIMED, "작업 시작"));
        assertErrorCode(
                () -> worker.recordStep(job.jobToken(), job.runId(), step(1, AgentStepType.TOOL_CALL, "다른 단계")),
                PaymentStopErrorCode.STEP_CONFLICT);
        worker.recordStep(
                job.jobToken(),
                job.runId(),
                new StepCommand(
                        2,
                        AgentStepType.CLAIM_EXTRACTED,
                        "주장 정리 010-9999-8888",
                        null,
                        Map.of("claims", List.of(Map.of("text", "계좌 110123456789 로 받음"))),
                        AgentStepStatus.DONE,
                        null,
                        null));

        assertErrorCode(
                () -> worker.recordStep(job.jobToken(), job.runId(), step(4, AgentStepType.TOOL_CALL, "건너뜀")),
                PaymentStopErrorCode.STEP_OUT_OF_ORDER);
        assertErrorCode(
                () -> worker.recordStep(job.jobToken(), job.runId(), step(3, AgentStepType.GATE_EVALUATED, "위조")),
                CommonErrorCode.INVALID_INPUT);

        List<AgentRunStep> steps = runQuery.steps(objectionId, job.runId(), 0);
        assertThat(steps).extracting(AgentRunStep::seq).containsExactly(1, 2);
        assertThat(steps.get(1).title()).isEqualTo("주장 정리 [연락처]");
        assertThat(steps.get(1).detail().toString()).contains("[계좌번호]").doesNotContain("110123456789");
        assertThat(runQuery.steps(objectionId, job.runId(), 1))
                .extracting(AgentRunStep::seq)
                .containsExactly(2);
    }

    @Test
    @DisplayName("FAILED 단계를 보내면 시도가 남아 있는 한 다시 대기열로 돌아간다")
    void failedStepRequeues() {
        long objectionId = receiveObjection(data.scenario(true));
        ClaimedJob job = claimOwn(objectionId);

        worker.recordStep(
                job.jobToken(),
                job.runId(),
                new StepCommand(
                        1, AgentStepType.FAILED, "도구 오류", "원장 조회 실패", null, AgentStepStatus.FAILED, null, null));

        assertThat(runStatus(objectionId)).isEqualTo("QUEUED");
        assertThat(claimOwn(objectionId).nextSeq()).isEqualTo(2);
    }

    @Test
    @DisplayName("범위 밖 거래를 근거로 든 판정안은 거부하고, 정상 판정안은 서버 게이트를 다시 계산해 기록한 뒤 실행을 끝낸다")
    void draftIsValidatedAndGateIsRecorded() {
        Scenario s = data.scenario(true);
        long objectionId = receiveObjection(s);
        ClaimedJob job = claimOwn(objectionId);
        worker.recordStep(job.jobToken(), job.runId(), step(1, AgentStepType.CLAIMED, "작업 시작"));

        assertErrorCode(
                () -> worker.submitDraft(
                        job.jobToken(), objectionId, draft(GateBranch.NORMAL_ACCEPT, "20991231WB9999999999")),
                PaymentStopErrorCode.EVIDENCE_OUT_OF_RANGE);

        String relation = worker.ledger(job.jobToken(), objectionId, null, null, null).stream()
                .filter(item -> !item.disputed())
                .findFirst()
                .orElseThrow()
                .transactionNumber();
        DraftResult result = worker.submitDraft(job.jobToken(), objectionId, draft(GateBranch.UNDETERMINED, relation));

        assertThat(result.agentBranch()).isEqualTo(GateBranch.UNDETERMINED);
        assertThat(result.gateBranch()).isEqualTo(GateBranch.NORMAL_ACCEPT);
        assertThat(runStatus(objectionId)).isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT cost_micro_usd FROM agent_run WHERE run_id = ?", Long.class, job.runId()))
                .isEqualTo(1_234L);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM gate_evaluation WHERE objection_id = ? AND purpose = 'ANALYSIS'",
                        Long.class,
                        objectionId))
                .isEqualTo(1L);
        assertThat(runQuery.steps(objectionId, job.runId(), 0)).last().satisfies(last -> {
            assertThat(last.seq()).isEqualTo(2);
            assertThat(last.type()).isEqualTo(AgentStepType.GATE_EVALUATED);
            assertThat(last.summary()).contains("다름");
        });
        assertThat(runQuery.latestDraft(objectionId))
                .hasValueSatisfying(saved -> assertThat(saved.branch()).isEqualTo(GateBranch.UNDETERMINED));

        // 응답이 유실돼 같은 토큰으로 다시 보내면 처음 결과를 돌려주고 단계를 더 남기지 않는다
        assertThat(worker.submitDraft(job.jobToken(), objectionId, draft(GateBranch.UNDETERMINED, relation)))
                .isEqualTo(result);
        assertThat(runQuery.steps(objectionId, job.runId(), 0)).hasSize(2);
    }

    @Test
    @DisplayName("진행 중인 분석이 있으면 재분석을 받지 않고, 끝난 뒤에는 새 실행을 넣는다")
    void reviewRequestRespectsActiveRun() {
        long objectionId = receiveObjection(data.scenario(true));

        assertErrorCode(() -> requestReview.request(objectionId, actor), PaymentStopErrorCode.AGENT_RUN_IN_PROGRESS);

        ClaimedJob job = claimOwn(objectionId);
        worker.submitDraft(job.jobToken(), objectionId, draft(GateBranch.UNDETERMINED));

        long rerun = requestReview.request(objectionId, actor);
        assertThat(rerun).isNotEqualTo(job.runId());
        assertThat(runQuery.runs(objectionId)).hasSize(2);
    }

    private ClaimedJob claimOwn(long objectionId) {
        return worker.claim(workerKey)
                .filter(job -> job.objectionId() == objectionId)
                .orElseThrow(() -> new AssertionError("이 테스트의 분석을 가져가지 못했습니다"));
    }

    private void expireLease(long objectionId) {
        jdbcTemplate.update(
                "UPDATE agent_run SET lease_until = ? WHERE objection_id = ? AND status = 'RUNNING'",
                LocalDateTime.now(clock).minusMinutes(1),
                objectionId);
    }

    private String runStatus(long objectionId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM agent_run WHERE objection_id = ? ORDER BY run_id DESC LIMIT 1",
                String.class,
                objectionId);
    }

    private long receiveObjection(Scenario s) {
        return receiveUseCase.receive(
                new ReceiveObjectionUseCase.Command(register(s), 1, "판매 대금으로 받은 돈입니다.", false, true, false), actor);
    }

    private long register(Scenario s) {
        return registerUseCase.register(
                new RegisterPaymentStopCaseUseCase.Command(
                        s.target(), List.of(s.disputed()), LocalDate.now(clock).plusDays(30)),
                actor);
    }

    private static StepCommand step(int seq, AgentStepType type, String title) {
        return new StepCommand(seq, type, title, null, null, AgentStepStatus.DONE, null, null);
    }

    private static DraftCommand draft(GateBranch branch, String... evidence) {
        return new DraftCommand(
                branch,
                List.of(evidence),
                List.of("거래 상대방과의 계약서나 주문 내역"),
                List.of(Map.of("text", "판매 대금", "quote", "판매 대금으로 받은 돈입니다.")),
                List.of(),
                List.of("거래 상대방 확인"),
                "소명 자료를 보완해 주세요.",
                "recorded",
                "v0",
                100L,
                50L,
                1_234L);
    }

    private void assertErrorCode(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
    }
}
