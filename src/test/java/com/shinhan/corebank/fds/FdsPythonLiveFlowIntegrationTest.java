package com.shinhan.corebank.fds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.audit.AccessReason;
import com.shinhan.corebank.fds.adapter.out.http.FastApiBehaviorScoreClient;
import com.shinhan.corebank.fds.api.FdsHoldDetail;
import com.shinhan.corebank.fds.api.FdsInvestigationInput;
import com.shinhan.corebank.fds.application.port.out.BehaviorFlowPort;
import com.shinhan.corebank.fds.application.port.out.BehaviorScorePort;
import com.shinhan.corebank.fds.application.service.BehaviorFlowService;
import com.shinhan.corebank.fds.application.service.BehaviorScoreService;
import com.shinhan.corebank.fds.application.service.FdsHoldDetailService;
import com.shinhan.corebank.fds.application.service.FdsInvestigationInputService;
import com.shinhan.corebank.transfer.api.TransferHeld;
import com.shinhan.corebank.transfer.api.TransferHoldReleaseMode;
import com.shinhan.corebank.transfer.api.TransferHoldUseCase;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

/** 실제 MySQL 보류·행동 이벤트와 실행 중인 Python 모델을 끝까지 연결한다. */
class FdsPythonLiveFlowIntegrationTest extends IntegrationTestSupport {
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private BehaviorFlowService flows;

    @Autowired
    private BehaviorFlowPort flowPort;

    @Autowired
    private BehaviorScorePort scores;

    @Autowired
    private TransferHoldUseCase holds;

    @Autowired
    private FdsHoldDetailService adminDetail;

    @Autowired
    private FdsInvestigationInputService agentInput;

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private Clock clock;

    @Test
    void storedScoreReachesAdminAndAgentFromActualPython() {
        String url = System.getenv("FDS_SCORE_TEST_URL");
        String token = System.getenv("FDS_SCORE_SERVICE_TOKEN");
        assumeTrue(url != null && token != null, "로컬 Python 서비스가 설정된 경우만 실행");

        UUID flowId = UUID.randomUUID();
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.ofHours(9)).minusSeconds(100);
        flows.submit(
                flowId,
                951001,
                new BehaviorFlowService.Batch(
                        List.of(
                                event(flowId, 1, "TRANSFER_ENTER", start, "D-01"),
                                event(flowId, 2, "RECIPIENT_CONFIRMED", start.plusSeconds(15), "D-01"),
                                event(flowId, 3, "STEP_BACK", start.plusSeconds(50), "D-01"),
                                event(flowId, 4, "AMOUNT_EDITED", start.plusSeconds(60), "D-01"),
                                event(flowId, 5, "SUBMIT_REQUESTED", start.plusSeconds(99), "D-02")),
                        71));
        long holdId = saveHold(flowId);
        var scoring = new BehaviorScoreService(
                holds,
                flowPort,
                scores,
                new FastApiBehaviorScoreClient(URI.create(url), token, new ObjectMapper()),
                transactions,
                clock);
        scoring.onHeld(new TransferHeld(
                holdId,
                951001,
                "A",
                TransferHoldReleaseMode.ON_APPROVAL,
                null,
                flowId.toString(),
                LocalDateTime.now(clock)));

        FdsHoldDetail detail = adminDetail.get(holdId, 951009, "127.0.0.1", AccessReason.OPERATION);
        FdsInvestigationInput input = agentInput.build(holdId, "inv-" + holdId);
        assertThat(detail.behaviorScoreStatus()).isEqualTo("AVAILABLE");
        assertThat(detail.behaviorRiskScore()).isBetween(0.0, 1.0);
        assertThat(input.behaviorSummary().behaviorRiskScore()).isEqualTo(detail.behaviorRiskScore());
        assertThat(input.behaviorSummary().modelVersion()).isEqualTo(detail.modelVersion());
        assertThat(detail.recipientAccountMasked()).endsWith("4567");
        assertThat(holds.findForFds(holdId).status()).isEqualTo("HELD");
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM audit_log WHERE event_type = 'FDS_HOLD_DETAIL_VIEW' AND customer_id = ?",
                        Long.class,
                        951009L))
                .isEqualTo(1L);

        long missingFlowHoldId = saveHold(UUID.randomUUID());
        scoring.onHeld(new TransferHeld(
                missingFlowHoldId,
                951001,
                "A",
                TransferHoldReleaseMode.ON_APPROVAL,
                null,
                holds.findForFds(missingFlowHoldId).flowId(),
                LocalDateTime.now(clock)));
        assertThat(adminDetail
                        .get(missingFlowHoldId, 951009, "127.0.0.1", AccessReason.OPERATION)
                        .behaviorScoreStatus())
                .isEqualTo("UNAVAILABLE");
        assertThat(agentInput
                        .build(missingFlowHoldId, "inv-" + missingFlowHoldId)
                        .behaviorSummary()
                        .behaviorRiskScore())
                .isNull();
        assertThat(holds.findForFds(missingFlowHoldId).status()).isEqualTo("HELD");
    }

    @Test
    void actualPythonRejectsWrongTokenAndScoreStaysUnavailable() {
        String url = System.getenv("FDS_SCORE_TEST_URL");
        String token = System.getenv("FDS_SCORE_SERVICE_TOKEN");
        assumeTrue(url != null && token != null, "로컬 Python 서비스가 설정된 경우만 실행");

        UUID flowId = UUID.randomUUID();
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.ofHours(9)).minusSeconds(100);
        flows.submit(
                flowId,
                951001,
                new BehaviorFlowService.Batch(
                        List.of(
                                event(flowId, 1, "TRANSFER_ENTER", start, "D-01"),
                                event(flowId, 2, "SUBMIT_REQUESTED", start.plusSeconds(99), "D-02")),
                        null));
        long holdId = saveHold(flowId);
        List<Long> financialStateBefore = financialState();
        var scoring = new BehaviorScoreService(
                holds,
                flowPort,
                scores,
                new FastApiBehaviorScoreClient(URI.create(url), token + "-wrong", new ObjectMapper()),
                transactions,
                clock);
        TransferHeld held = new TransferHeld(
                holdId,
                951001,
                "A",
                TransferHoldReleaseMode.ON_APPROVAL,
                null,
                flowId.toString(),
                LocalDateTime.now(clock));

        scoring.onHeld(held);
        scoring.onHeld(held);

        assertThat(scores.find(holdId).status()).isEqualTo("UNAVAILABLE");
        assertThat(scores.find(holdId).unavailableReason()).isEqualTo("INFERENCE_SERVICE_UNAVAILABLE");
        assertThat(scores.find(holdId).behaviorRiskScore()).isNull();
        assertThat(adminDetail
                        .get(holdId, 951009, "127.0.0.1", AccessReason.OPERATION)
                        .behaviorScoreUnavailableReason())
                .isEqualTo("INFERENCE_SERVICE_UNAVAILABLE");
        assertThat(agentInput.build(holdId, "inv-" + holdId).behaviorSummary().behaviorRiskScore())
                .isNull();
        assertThat(holds.findForFds(holdId).status()).isEqualTo("HELD");
        assertThat(financialState()).isEqualTo(financialStateBefore);
    }

    @ParameterizedTest
    @CsvSource({"503,MODEL_UNAVAILABLE", "500,INFERENCE_FAILED", "504,INFERENCE_TIMEOUT", "200,INFERENCE_FAILED"})
    void httpFailureContractsNeverPersistSuccessfulScore(int status, String reason) throws Exception {
        HttpServer pythonStub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        pythonStub.createContext("/internal/fds/behavior-score/predict", exchange -> {
            byte[] body = (status == 200
                            ? "{\"code\":\"0000\",\"data\":{\"behaviorRiskScore\":1.2}}"
                            : "{\"code\":\"SCORE_ERROR\",\"data\":null}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        pythonStub.start();
        try {
            UUID flowId = UUID.randomUUID();
            OffsetDateTime start = OffsetDateTime.now(ZoneOffset.ofHours(9)).minusSeconds(100);
            flows.submit(
                    flowId,
                    951001,
                    new BehaviorFlowService.Batch(
                            List.of(
                                    event(flowId, 1, "TRANSFER_ENTER", start, "D-01"),
                                    event(flowId, 2, "SUBMIT_REQUESTED", start.plusSeconds(99), "D-02")),
                            null));
            long holdId = saveHold(flowId);
            var scoring = new BehaviorScoreService(
                    holds,
                    flowPort,
                    scores,
                    new FastApiBehaviorScoreClient(
                            URI.create("http://127.0.0.1:"
                                    + pythonStub.getAddress().getPort()),
                            "local-test-token",
                            new ObjectMapper()),
                    transactions,
                    clock);

            scoring.onHeld(new TransferHeld(
                    holdId,
                    951001,
                    "A",
                    TransferHoldReleaseMode.ON_APPROVAL,
                    null,
                    flowId.toString(),
                    LocalDateTime.now(clock)));

            assertThat(scores.find(holdId).status()).isEqualTo("UNAVAILABLE");
            assertThat(scores.find(holdId).unavailableReason()).isEqualTo(reason);
            assertThat(scores.find(holdId).behaviorRiskScore()).isNull();
            assertThat(scores.find(holdId).modelVersion()).isNull();
            assertThat(scores.find(holdId).featureVersion()).isNull();
            assertThat(holds.findForFds(holdId).status()).isEqualTo("HELD");
        } finally {
            pythonStub.stop(0);
        }
    }

    private List<Long> financialState() {
        return List.of(
                jdbc.queryForObject("SELECT COUNT(*) FROM ledger_entry", Long.class),
                jdbc.queryForObject("SELECT COALESCE(SUM(balance), 0) FROM account", Long.class),
                jdbc.queryForObject(
                        "SELECT COALESCE(SUM(used_amount), 0) FROM transfer_limit_daily_usage", Long.class));
    }

    private static BehaviorFlowService.EventInput event(
            UUID id, int sequence, String type, OffsetDateTime time, String step) {
        return new BehaviorFlowService.EventInput(id, sequence, type, time, step);
    }

    private long saveHold(UUID flowId) {
        var values = new MapSqlParameterSource()
                .addValue("customer_id", 951001L)
                .addValue("withdrawal_account_id", 951002L)
                .addValue("deposit_account_number", "0881234567")
                .addValue("amount", 500000L)
                .addValue("channel", "WB")
                .addValue("flow_id", flowId.toString())
                .addValue("policy_code", "A")
                .addValue("release_mode", "ON_APPROVAL")
                .addValue("status", "HELD")
                .addValue("authenticated_at", LocalDateTime.now(clock))
                .addValue("held_at", LocalDateTime.now(clock));
        return new SimpleJdbcInsert(jdbc)
                .withTableName("transfer_hold")
                .usingGeneratedKeyColumns("transfer_hold_id")
                .executeAndReturnKey(values)
                .longValue();
    }
}
