package com.shinhan.corebank.paymentstop.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.support.CustomerTestFixture;
import com.shinhan.corebank.common.audit.AccessReason;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
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
import com.shinhan.corebank.paymentstop.domain.external.ExternalCheckReply;
import com.shinhan.corebank.paymentstop.domain.gate.GateBranch;
import com.shinhan.corebank.paymentstop.domain.gate.GateResult;
import com.shinhan.corebank.paymentstop.support.PaymentStopTestData;
import com.shinhan.corebank.paymentstop.support.PaymentStopTestData.Scenario;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 담당자 재량 판정(ADR-0011). 게이트가 닫은 수용과 일부 지급정지를 사유, 2인 승인, 승인자 확인으로 확정한다. 소송 계속, 사유 조항
 * 불일치, 부정 회신은 재량으로도 넘지 못한다. 서비스가 커밋한 결과를 JdbcTemplate 으로 다시 읽어 영속성 캐시를 피한다.
 */
@DisplayName("이의제기 재량 판정 통합 테스트")
class DiscretionaryDecisionIntegrationTest extends IntegrationTestSupport {

    private static final String DISCRETIONARY_REASON = "관계 거래는 없지만 판매 계약서와 대화 기록으로 정상 거래가 확인됩니다.";

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
    @DisplayName("근거가 없어 게이트가 닫힌 수용을 재량으로 쓰면 승인 대기가 되고, 승인자가 확인하고 승인하면 지급정지가 풀린다")
    void discretionaryAccept_writeThenApprove() {
        Scenario s = data.scenario(false);
        long objectionId = reviewing(register(s), 1, false);

        ObjectionDecision pending = discretionary(objectionId, DecisionResult.ACCEPTED, DISCRETIONARY_REASON);

        assertThat(pending.status()).isEqualTo(DecisionStatus.PENDING_APPROVAL);
        assertThat(pending.discretionary()).isTrue();
        assertThat(pending.discretionaryReason()).isEqualTo(DISCRETIONARY_REASON);
        assertThat(pending.gateBranchAtWrite()).isEqualTo(GateBranch.UNDETERMINED);
        assertThat(data.status(s.target())).isEqualTo("SUSPENDED");
        Map<String, Object> row = decisionRow(pending.decisionId());
        assertThat(row.get("discretionary")).isEqualTo(true);
        assertThat(row.get("discretionary_reason")).isEqualTo(DISCRETIONARY_REASON);
        assertThat(row.get("gate_branch_at_write")).isEqualTo("UNDETERMINED");
        assertThat(auditDetail(customerId, "OBJECTION_DECISION_SUBMIT"))
                .contains("\"discretionary\": true")
                .contains(DISCRETIONARY_REASON)
                .contains("\"gateBranchAtWrite\": \"UNDETERMINED\"");

        ObjectionDecision confirmed = approveUseCase.approve(objectionId, pending.decisionId(), true, approver);

        assertThat(confirmed.status()).isEqualTo(DecisionStatus.CONFIRMED);
        assertThat(confirmed.discretionary()).isTrue();
        assertThat(data.status(s.target())).isEqualTo("ACTIVE");
        assertThat(data.objectionStatus(objectionId)).isEqualTo("DECIDED");
        assertThat(decisionRow(pending.decisionId()).get("discretionary")).isEqualTo(true);
        assertThat(auditDetail(approverId, "OBJECTION_DECISION_CONFIRM"))
                .contains("\"discretionary\": true")
                .contains("\"discretionaryAcknowledged\": true");
        assertThat(data.countAudit(approverId, "PAYMENT_STOP_RELEASE")).isEqualTo(1L);
        // 통지 기록 동작은 일반 수용과 같다
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
    @DisplayName("사유 3호에서 근거가 없어 닫힌 일부 지급정지는 재량으로 쓰고 승인하면 피해금만 보류한다")
    void discretionaryPartial_holdsDisputedAmount() {
        Scenario s = data.scenario(false);
        long objectionId = reviewing(register(s), 3, false);

        ObjectionDecision pending = discretionary(objectionId, DecisionResult.PARTIAL, DISCRETIONARY_REASON);
        ObjectionDecision confirmed = approveUseCase.approve(objectionId, pending.decisionId(), true, approver);

        assertThat(confirmed.appliedHoldAmount()).isEqualTo(PaymentStopTestData.DISPUTED_AMOUNT);
        assertThat(data.holdAmount(s.target())).isEqualTo(PaymentStopTestData.DISPUTED_AMOUNT);
    }

    @Test
    @DisplayName("재량 사유가 없거나 20자 미만이거나 1000자를 넘으면 CMN0001이고 판정이 남지 않는다")
    void discretionaryReason_isValidated() {
        long objectionId = reviewing(register(data.scenario(false)), 1, false);

        for (String reason : new String[] {null, " ", "짧은 사유입니다", "가".repeat(1001)}) {
            assertErrorCode(
                    () -> discretionary(objectionId, DecisionResult.ACCEPTED, reason), CommonErrorCode.INVALID_INPUT);
        }
        assertThat(decisionCount(objectionId)).isZero();
        assertThat(data.objectionStatus(objectionId)).isEqualTo("UNDER_REVIEW");
    }

    @Test
    @DisplayName("게이트가 이미 연 판정과 불수용에는 재량 표시를 할 수 없다(CMN0001)")
    void discretionaryOnOpenGate_isRejected() {
        long objectionId = reviewing(register(data.scenario(true)), 1, false);

        assertErrorCode(
                () -> discretionary(objectionId, DecisionResult.ACCEPTED, DISCRETIONARY_REASON),
                CommonErrorCode.INVALID_INPUT);
        assertErrorCode(
                () -> discretionary(objectionId, DecisionResult.REJECTED, DISCRETIONARY_REASON),
                CommonErrorCode.INVALID_INPUT);
        assertThat(decisionCount(objectionId)).isZero();
    }

    @Test
    @DisplayName("재량 표시 없이 닫힌 판정을 쓰면 지금처럼 PST0301이다")
    void closedGateWithoutDiscretionary_isReleaseNotAllowed() {
        long objectionId = reviewing(register(data.scenario(false)), 1, false);

        assertErrorCode(
                () -> writeUseCase.write(
                        new WriteDecisionUseCase.Command(
                                objectionId, DecisionResult.ACCEPTED, "관계 거래 확인", "관계 거래 원장 확인"),
                        actor),
                PaymentStopErrorCode.RELEASE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("소송 계속 중이면 재량으로도 수용할 수 없다(PST0310)")
    void litigationPending_isHardBlock() {
        Scenario s = data.scenario(false);
        long objectionId = reviewing(register(s), 1, true);

        assertErrorCode(
                () -> discretionary(objectionId, DecisionResult.ACCEPTED, DISCRETIONARY_REASON),
                PaymentStopErrorCode.DISCRETIONARY_NOT_ALLOWED);
        assertThat(data.status(s.target())).isEqualTo("SUSPENDED");
        assertThat(decisionCount(objectionId)).isZero();
    }

    @Test
    @DisplayName("사유 조항과 판정 범위가 맞지 않으면 재량으로도 쓸 수 없다(PST0310)")
    void clauseMismatch_isHardBlock() {
        long clauseOne = reviewing(register(data.scenario(false)), 1, false);
        assertErrorCode(
                () -> discretionary(clauseOne, DecisionResult.PARTIAL, DISCRETIONARY_REASON),
                PaymentStopErrorCode.DISCRETIONARY_NOT_ALLOWED);

        long clauseThree = reviewing(register(data.scenario(false)), 3, false);
        assertErrorCode(
                () -> discretionary(clauseThree, DecisionResult.ACCEPTED, DISCRETIONARY_REASON),
                PaymentStopErrorCode.DISCRETIONARY_NOT_ALLOWED);
    }

    @Test
    @DisplayName("외부 확인 부정 회신(가담 의심)이 있으면 재량으로도 수용할 수 없다(PST0310)")
    void negativeReply_isHardBlock() {
        long objectionId = reviewing(register(data.scenario(true)), 1, false);
        externalCheckUseCase.request(
                objectionId, ExternalAgency.POLICE, ExternalCheckReply.INVOLVEMENT_SUSPECTED, actor);

        assertErrorCode(
                () -> discretionary(objectionId, DecisionResult.ACCEPTED, DISCRETIONARY_REASON),
                PaymentStopErrorCode.DISCRETIONARY_NOT_ALLOWED);
    }

    @Test
    @DisplayName("재량 판정도 작성자는 승인할 수 없다(PST0309)")
    void authorCannotApproveDiscretionary() {
        long objectionId = reviewing(register(data.scenario(false)), 1, false);
        ObjectionDecision pending = discretionary(objectionId, DecisionResult.ACCEPTED, DISCRETIONARY_REASON);

        assertErrorCode(
                () -> approveUseCase.approve(objectionId, pending.decisionId(), true, actor),
                PaymentStopErrorCode.APPROVER_SAME_AS_AUTHOR);
    }

    @Test
    @DisplayName("재량 판정을 승인자가 확인하지 않고 승인하면 PST0311이고 승인 대기로 남는다")
    void approveWithoutAcknowledgement_isRejected() {
        Scenario s = data.scenario(false);
        long objectionId = reviewing(register(s), 1, false);
        ObjectionDecision pending = discretionary(objectionId, DecisionResult.ACCEPTED, DISCRETIONARY_REASON);

        assertErrorCode(
                () -> approveUseCase.approve(objectionId, pending.decisionId(), false, approver),
                PaymentStopErrorCode.DISCRETIONARY_ACK_REQUIRED);
        assertErrorCode(
                () -> approveUseCase.approve(objectionId, pending.decisionId(), approver),
                PaymentStopErrorCode.DISCRETIONARY_ACK_REQUIRED);

        assertThat(data.status(s.target())).isEqualTo("SUSPENDED");
        assertThat(data.objectionStatus(objectionId)).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    @DisplayName("작성 뒤 승인 전에 부정 회신이 오면 확인해도 승인되지 않는다(PST0310, D11)")
    void negativeReplyAfterWrite_blocksApproval() {
        Scenario s = data.scenario(false);
        long objectionId = reviewing(register(s), 1, false);
        ObjectionDecision pending = discretionary(objectionId, DecisionResult.ACCEPTED, DISCRETIONARY_REASON);

        externalCheckUseCase.request(objectionId, ExternalAgency.POLICE, ExternalCheckReply.DOCUMENT_FORGED, actor);

        assertErrorCode(
                () -> approveUseCase.approve(objectionId, pending.decisionId(), true, approver),
                PaymentStopErrorCode.DISCRETIONARY_NOT_ALLOWED);
        assertThat(data.status(s.target())).isEqualTo("SUSPENDED");
        assertThat(data.objectionStatus(objectionId)).isEqualTo("PENDING_APPROVAL");
        assertThat(data.countAudit(approverId, "PAYMENT_STOP_RELEASE")).isZero();
    }

    @Test
    @DisplayName("작성 뒤 게이트가 열리면 재량 표시를 남긴 채 확정한다")
    void gateOpenedAfterWrite_keepsDiscretionary() {
        Scenario s = data.scenario(false);
        long objectionId = reviewing(register(s), 1, false);
        ObjectionDecision pending = discretionary(objectionId, DecisionResult.ACCEPTED, DISCRETIONARY_REASON);

        externalCheckUseCase.request(objectionId, ExternalAgency.POLICE, ExternalCheckReply.OWNER_UNRELATED, actor);
        ObjectionDecision confirmed = approveUseCase.approve(objectionId, pending.decisionId(), true, approver);

        assertThat(confirmed.discretionary()).isTrue();
        assertThat(confirmed.gateBranchAtWrite()).isEqualTo(GateBranch.UNDETERMINED);
        assertThat(data.status(s.target())).isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT branch FROM gate_evaluation WHERE objection_id = ? AND purpose = 'DECISION'",
                        String.class,
                        objectionId))
                .isEqualTo("NORMAL_ACCEPT");
    }

    @Test
    @DisplayName("일반 판정은 재량 표시 없이 쓰고 확인 없이 승인되며 작성 시점 갈래를 남긴다")
    void ordinaryDecision_isBackwardCompatible() {
        Scenario s = data.scenario(true);
        long objectionId = reviewing(register(s), 1, false);

        ObjectionDecision pending = writeUseCase.write(
                new WriteDecisionUseCase.Command(
                        objectionId, DecisionResult.ACCEPTED, "관계 거래 확인", "관계 거래 원장 확인", false, "무시되는 사유"),
                actor);
        ObjectionDecision confirmed = approveUseCase.approve(objectionId, pending.decisionId(), approver);

        assertThat(confirmed.discretionary()).isFalse();
        assertThat(confirmed.discretionaryReason()).isNull();
        assertThat(confirmed.gateBranchAtWrite()).isEqualTo(GateBranch.NORMAL_ACCEPT);
        assertThat(data.status(s.target())).isEqualTo("ACTIVE");
        Map<String, Object> row = decisionRow(pending.decisionId());
        assertThat(row.get("discretionary")).isEqualTo(false);
        assertThat(row.get("discretionary_reason")).isNull();
    }

    @Test
    @DisplayName("상세 게이트는 재량으로 쓸 수 있는 판정을 알려 주고, 목록은 승인 대기 판정의 재량 여부를 보인다")
    void detailAndListExposeDiscretionary() {
        long objectionId = reviewing(register(data.scenario(false)), 1, false);
        long ordinaryId = reviewing(register(data.scenario(true)), 1, false);

        GateResult gate =
                queryUseCase.detail(objectionId, actor, AccessReason.OPERATION).gate();
        assertThat(gate.allowsDiscretionary(DecisionResult.ACCEPTED)).isTrue();
        assertThat(gate.allowsDiscretionary(DecisionResult.PARTIAL)).isFalse();
        assertThat(gate.allowsDiscretionary(DecisionResult.REJECTED)).isFalse();

        discretionary(objectionId, DecisionResult.ACCEPTED, DISCRETIONARY_REASON);
        writeUseCase.write(
                new WriteDecisionUseCase.Command(ordinaryId, DecisionResult.ACCEPTED, "관계 거래 확인", "관계 거래 원장 확인"),
                actor);

        List<ObjectionQueryUseCase.Summary> list = queryUseCase.list();
        assertThat(list)
                .filteredOn(summary -> summary.objection().getObjectionId() == objectionId)
                .singleElement()
                .satisfies(summary -> assertThat(summary.discretionary()).isTrue());
        assertThat(list)
                .filteredOn(summary -> summary.objection().getObjectionId() == ordinaryId)
                .singleElement()
                .satisfies(summary -> assertThat(summary.discretionary()).isFalse());
    }

    @Test
    @DisplayName("재량 판정인데 사유가 없는 행은 DB CHECK 가 막는다")
    void checkConstraint_requiresReason() {
        long objectionId = reviewing(register(data.scenario(true)), 1, false);
        ObjectionDecision pending = writeUseCase.write(
                new WriteDecisionUseCase.Command(objectionId, DecisionResult.ACCEPTED, "관계 거래 확인", "관계 거래 원장 확인"),
                actor);

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "UPDATE objection_decision SET discretionary = TRUE WHERE decision_id = ?",
                        pending.decisionId()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_objection_decision_discretionary");
    }

    private ObjectionDecision discretionary(long objectionId, DecisionResult result, String reason) {
        return writeUseCase.write(
                new WriteDecisionUseCase.Command(
                        objectionId,
                        result,
                        "담당자 재량 판정",
                        result == DecisionResult.REJECTED ? null : "판매 계약서 확인",
                        true,
                        reason),
                actor);
    }

    private Map<String, Object> decisionRow(long decisionId) {
        return jdbcTemplate.queryForMap(
                "SELECT discretionary, discretionary_reason, gate_branch_at_write FROM objection_decision"
                        + " WHERE decision_id = ?",
                decisionId);
    }

    private long decisionCount(long objectionId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM objection_decision WHERE objection_id = ?", Long.class, objectionId);
    }

    private String auditDetail(long actorId, String eventType) {
        return jdbcTemplate.queryForObject(
                "SELECT CAST(detail AS CHAR) FROM audit_log WHERE customer_id = ? AND event_type = ?"
                        + " ORDER BY audit_log_id DESC LIMIT 1",
                String.class,
                actorId,
                eventType);
    }

    private long register(Scenario s) {
        return registerUseCase.register(
                new RegisterPaymentStopCaseUseCase.Command(
                        s.target(), List.of(s.disputed()), LocalDate.now(clock).plusDays(30)),
                actor);
    }

    private long reviewing(long caseId, int clause, boolean litigationPending) {
        long objectionId = receiveUseCase.receive(
                new ReceiveObjectionUseCase.Command(caseId, clause, "판매 대금으로 받은 돈입니다.", false, true, litigationPending),
                actor);
        progressUseCase.confirmCompleteness(objectionId, actor);
        return objectionId;
    }

    private void assertErrorCode(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
    }
}
