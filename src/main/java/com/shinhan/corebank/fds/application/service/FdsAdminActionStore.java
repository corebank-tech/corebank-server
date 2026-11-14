package com.shinhan.corebank.fds.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.fds.application.service.FdsRiskAccountService.Check;
import com.shinhan.corebank.fds.domain.exception.FdsErrorCode;
import com.shinhan.corebank.transfer.api.TransferHoldForFds;
import com.shinhan.corebank.transfer.api.TransferHoldUseCase;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 콜과 P4 호출 전 조치 의도를 각각 독립 트랜잭션으로 내구화한다. */
@Service
@RequiredArgsConstructor
public class FdsAdminActionStore {
    private static final List<String> CALL_RESULTS =
            List.of("CUSTOMER_CONFIRMED", "CUSTOMER_DENIED", "NO_ANSWER", "INCONCLUSIVE");

    private final JdbcTemplate jdbc;
    private final TransferHoldUseCase holds;
    private final FdsRiskAccountService risks;
    private final Clock clock;

    @Transactional
    public CallRecord recordCall(
            long holdId, String idempotencyKey, long adminId, String result, String investigationReason) {
        if (!CALL_RESULTS.contains(result) || investigationReason == null || investigationReason.isBlank()) invalid();
        holds.findForFds(holdId);
        String fingerprint = sha256(holdId + "|" + adminId + "|" + result + "|" + investigationReason);
        CallRecord existing = callByKey(idempotencyKey);
        if (existing != null) {
            if (!fingerprint.equals(existing.fingerprint())) conflict();
            return existing;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        KeyHolder key = new GeneratedKeyHolder();
        try {
            jdbc.update(
                    connection -> {
                        var statement = connection.prepareStatement(
                                "INSERT INTO fds_admin_call_record "
                                        + "(transfer_hold_id,idempotency_key,request_fingerprint,call_result,admin_id,investigation_reason,recorded_at) "
                                        + "VALUES (?,?,?,?,?,?,?)",
                                new String[] {"call_record_id"});
                        statement.setLong(1, holdId);
                        statement.setString(2, idempotencyKey);
                        statement.setString(3, fingerprint);
                        statement.setString(4, result);
                        statement.setLong(5, adminId);
                        statement.setString(6, investigationReason);
                        statement.setObject(7, now);
                        return statement;
                    },
                    key);
        } catch (DuplicateKeyException exception) {
            // 동시 동일 요청은 유니크 제약의 승자 행을 다시 읽어 같은 결과로 수렴시킨다.
            CallRecord concurrent = callByKeyForUpdate(idempotencyKey);
            if (concurrent == null) conflict();
            if (!fingerprint.equals(concurrent.fingerprint())) conflict();
            return concurrent;
        }
        // 최초 요청도 DB 정본을 반환해 재전달 응답의 ID와 DATETIME(6) 정밀도를 동일하게 유지한다.
        CallRecord stored = callByKey(idempotencyKey);
        if (stored == null) conflict();
        return stored;
    }

    @Transactional
    public Action prepare(long holdId, String idempotencyKey, long actorId, String actionType, String reasonCode) {
        String fingerprint = sha256(holdId + "|" + actorId + "|" + actionType + "|" + reasonCode);
        Action existing = actionByKey(idempotencyKey);
        if (existing != null) {
            if (!fingerprint.equals(existing.fingerprint())) conflict();
            return existing;
        }
        TransferHoldForFds hold = holds.findForFds(holdId);
        if (!"HELD".equals(hold.status()) || !"ON_APPROVAL".equals(hold.releaseMode())) conflict();
        Check risk = risks.checkForAction(hold.recipientBankCode(), hold.depositAccountNumber());
        CallRow call = latestCallForUpdate(holdId);
        validateAction(actionType, reasonCode, risk, call);
        String actionId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            jdbc.update(
                    "INSERT INTO fds_admin_action "
                            + "(action_id,transfer_hold_id,idempotency_key,request_fingerprint,actor_id,action_type,reason_code,"
                            + "call_record_id,risk_status,risk_account_id,dataset_version,unavailable_reason,action_status,created_at,updated_at) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?, 'PENDING',?,?)",
                    actionId,
                    holdId,
                    idempotencyKey,
                    fingerprint,
                    actorId,
                    actionType,
                    reasonCode,
                    call == null ? null : call.id(),
                    risk.status(),
                    risk.riskAccountId(),
                    risk.datasetVersion(),
                    risk.unavailableReason(),
                    now,
                    now);
        } catch (DuplicateKeyException exception) {
            // 동일 키 경합은 승자 결과를 복구하고 다른 미완료 조치 제약은 계약 충돌로 숨긴다.
            Action concurrent = actionByKeyForUpdate(idempotencyKey);
            if (concurrent == null) conflict();
            if (!fingerprint.equals(concurrent.fingerprint())) conflict();
            return concurrent;
        }
        return actionById(actionId);
    }

    @Transactional
    public Action reconcile(Action action, TransferHoldForFds actual) {
        boolean matches = "APPROVE".equals(action.actionType())
                ? List.of("EXECUTED", "FAILED").contains(actual.status())
                        && java.util.Objects.equals(action.actorId(), actual.actorId())
                : "REJECTED".equals(actual.status())
                        && java.util.Objects.equals(action.actorId(), actual.actorId())
                        && java.util.Objects.equals(action.reasonCode(), actual.reasonCode());
        // EXECUTING 같은 중간 상태는 PENDING으로 남겨 다음 복구 주기에 다시 대조한다.
        if (!matches && !terminal(actual.status())) return actionById(action.actionId());
        String target = matches ? "COMPLETED" : "CONFLICT";
        int updated = jdbc.update(
                "UPDATE fds_admin_action SET action_status=?,failure_reason=?,p4_status=?,p4_actor_id=?,"
                        + "p4_reason_code=?,updated_at=? WHERE action_id=? AND action_status='PENDING'",
                target,
                matches ? null : "P4_RESULT_MISMATCH",
                actual.status(),
                actual.actorId(),
                actual.reasonCode(),
                LocalDateTime.now(clock),
                action.actionId());
        if (matches && updated == 1) {
            jdbc.update(
                    "INSERT INTO fds_admin_action_audit "
                            + "(action_id,transfer_hold_id,actor_id,action_type,reason_code,call_record_id,risk_status,"
                            + "risk_account_id,dataset_version,p4_status,p4_actor_id,p4_reason_code,completed_at) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE action_id=action_id",
                    action.actionId(),
                    action.holdId(),
                    action.actorId(),
                    action.actionType(),
                    action.reasonCode(),
                    action.callRecordId(),
                    action.riskStatus(),
                    action.riskAccountId(),
                    action.datasetVersion(),
                    actual.status(),
                    actual.actorId(),
                    actual.reasonCode(),
                    LocalDateTime.now(clock));
        }
        return actionById(action.actionId());
    }

    public List<Action> pending() {
        return jdbc.query(
                "SELECT action_id FROM fds_admin_action WHERE action_status='PENDING' ORDER BY created_at",
                (rs, row) -> actionById(rs.getString(1)));
    }

    private void validateAction(String type, String reason, Check risk, CallRow call) {
        if ("MATCH".equals(risk.status())) {
            if (!"REJECT".equals(type) || !"RISK_ACCOUNT_MATCH".equals(reason)) conflict();
            return;
        }
        if (call == null) conflict();
        if ("APPROVE".equals(type) && !"CUSTOMER_CONFIRMED".equals(call.result())) conflict();
        if ("REJECT".equals(type) && (!"CUSTOMER_DENIED".equals(call.result()) || !"CUSTOMER_DENIED".equals(reason)))
            conflict();
        if (!("APPROVE".equals(type) || "REJECT".equals(type))) invalid();
    }

    private CallRecord callByKey(String key) {
        return callByKey(key, "");
    }

    private CallRecord callByKeyForUpdate(String key) {
        return callByKey(key, " FOR UPDATE");
    }

    private CallRecord callByKey(String key, String lockClause) {
        return jdbc.query(
                "SELECT call_record_id,request_fingerprint,call_result,recorded_at FROM fds_admin_call_record "
                        + "WHERE idempotency_key=?" + lockClause,
                rs -> rs.next()
                        ? new CallRecord(
                                rs.getLong(1),
                                rs.getString(2),
                                rs.getString(3),
                                rs.getTimestamp(4).toLocalDateTime())
                        : null,
                key);
    }

    private CallRow latestCallForUpdate(long holdId) {
        return jdbc.query(
                "SELECT call_record_id,call_result FROM fds_admin_call_record WHERE transfer_hold_id=? "
                        + "ORDER BY recorded_at DESC,call_record_id DESC LIMIT 1 FOR UPDATE",
                rs -> rs.next() ? new CallRow(rs.getLong(1), rs.getString(2)) : null,
                holdId);
    }

    private Action actionByKey(String key) {
        return actionQuery("idempotency_key", key);
    }

    private Action actionByKeyForUpdate(String key) {
        return actionQuery("idempotency_key", key, " FOR UPDATE");
    }

    public Action actionById(String id) {
        return actionQuery("action_id", id);
    }

    private Action actionQuery(String column, String value) {
        return actionQuery(column, value, "");
    }

    private Action actionQuery(String column, String value, String lockClause) {
        return jdbc.query(
                "SELECT action_id,transfer_hold_id,request_fingerprint,actor_id,action_type,reason_code,call_record_id,"
                        + "risk_status,risk_account_id,dataset_version,action_status FROM fds_admin_action WHERE "
                        + column + "=?" + lockClause,
                rs -> rs.next()
                        ? new Action(
                                rs.getString(1),
                                rs.getLong(2),
                                rs.getString(3),
                                rs.getLong(4),
                                rs.getString(5),
                                rs.getString(6),
                                (Long) rs.getObject(7),
                                rs.getString(8),
                                (Long) rs.getObject(9),
                                rs.getString(10),
                                rs.getString(11))
                        : null,
                value);
    }

    private static boolean terminal(String status) {
        return List.of("EXECUTED", "REJECTED", "FAILED", "CANCELLED", "EXPIRED").contains(status);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void invalid() {
        throw new BusinessException(FdsErrorCode.INVALID_EVENT);
    }

    private static void conflict() {
        throw new BusinessException(FdsErrorCode.FLOW_CONFLICT);
    }

    private record CallRow(long id, String result) {}

    public record CallRecord(long callRecordId, String fingerprint, String callResult, LocalDateTime recordedAt) {}

    public record Action(
            String actionId,
            long holdId,
            String fingerprint,
            Long actorId,
            String actionType,
            String reasonCode,
            Long callRecordId,
            String riskStatus,
            Long riskAccountId,
            String datasetVersion,
            String status) {}
}
