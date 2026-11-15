package com.shinhan.corebank.fds.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.fds.domain.exception.FdsErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 활성 P6 데이터셋만 정본으로 위험 계좌를 판정하고 버전 전환을 직렬화한다. */
@Service
@RequiredArgsConstructor
public class FdsRiskAccountService {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcTemplate jdbc;
    private final Clock clock;

    /** 계좌가 활성 데이터셋 또는 관리자 확정 위험 계좌인지 판정한다. */
    public Check check(String bankCode, String accountNumber) {
        return check(bankCode, accountNumber, false);
    }

    /** 관리자 조치 시 활성 버전 포인터를 잠그고 위험 계좌 여부를 판정한다. */
    @Transactional
    public Check checkForAction(String bankCode, String accountNumber) {
        return check(bankCode, accountNumber, true);
    }

    private Check check(String bankCode, String accountNumber, boolean lockActiveVersion) {
        try {
            String version = jdbc.query(
                    "SELECT dataset_version FROM fds_risk_active_version WHERE singleton_id=1"
                            + (lockActiveVersion ? " FOR UPDATE" : ""),
                    rs -> rs.next() ? rs.getString(1) : null);
            if (version == null) return Check.unavailable(null, "ACTIVE_DATASET_MISSING");
            List<Long> ids = jdbc.queryForList(
                    "SELECT risk_account_id FROM fds_risk_account "
                            + "WHERE (dataset_version=? OR source_type='ADMIN_CONFIRMED') AND bank_code=? AND account_number=? "
                            + "ORDER BY (source_type='ADMIN_CONFIRMED') DESC, risk_account_id"
                            + (lockActiveVersion ? " FOR UPDATE" : ""),
                    Long.class,
                    version,
                    normalizeBank(bankCode),
                    normalizeAccount(accountNumber));
            // 관리자 조치는 복합 유니크 키의 행·갭 잠금으로 활성 버전 전환과 동일 스냅샷을 유지한다.
            return ids.isEmpty() ? Check.noMatch(version) : Check.match(ids.getFirst(), version);
        } catch (DataAccessException exception) {
            return Check.unavailable(null, "RISK_LOOKUP_FAILED");
        }
    }

    /** 사기 확정된 조치의 위험 계좌 등록 의도를 ACTIVE로 전환하고 사건 근거를 보존한다. */
    @Transactional
    public Activation activateAdminConfirmed(String actionId) {
        // 활성 포인터를 먼저 잠가 데이터셋 전환과 관리자 등록이 같은 잠금 순서를 사용하게 한다.
        String activeVersion = jdbc.query(
                "SELECT dataset_version FROM fds_risk_active_version WHERE singleton_id=1 FOR UPDATE",
                rs -> rs.next() ? rs.getString(1) : null);
        if (activeVersion == null) throw new BusinessException(FdsErrorCode.RISK_REGISTRATION_FAILED);
        Registration registration = registrationForUpdate(actionId);
        if (registration == null) throw new BusinessException(FdsErrorCode.NOT_FOUND);
        if ("ACTIVE".equals(registration.status())) {
            return new Activation(registration.registrationId(), registration.riskAccountId(), activeVersion, true);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        // 활성 버전의 SEED 행을 단순 재사용하지 않고 ADMIN_CONFIRMED 행을 정본으로 관리한다.
        Long existing = findExistingAdminRiskAccount(registration.bankCode(), registration.accountNumber());
        if (existing == null) {
            try {
                jdbc.update(
                        "INSERT INTO fds_risk_account "
                                + "(dataset_version,bank_code,account_number,source_type,reason,created_at) "
                                + "VALUES (?,?,?,'ADMIN_CONFIRMED','FRAUD_CONFIRMED',?)",
                        activeVersion,
                        registration.bankCode(),
                        registration.accountNumber(),
                        now);
            } catch (org.springframework.dao.DuplicateKeyException ignored) {
                // 동일 버전에 이미 SEED 행이 존재하면 ADMIN_CONFIRMED로 상태를 승격하여 영구 보존한다.
                jdbc.update(
                        "UPDATE fds_risk_account SET source_type='ADMIN_CONFIRMED', reason='FRAUD_CONFIRMED' "
                                + "WHERE bank_code=? AND account_number=? AND dataset_version=?",
                        registration.bankCode(),
                        registration.accountNumber(),
                        activeVersion);
            }
            existing = findExistingAdminRiskAccount(registration.bankCode(), registration.accountNumber());
        }
        if (existing == null) throw new BusinessException(FdsErrorCode.RISK_REGISTRATION_FAILED);
        jdbc.update(
                "UPDATE fds_risk_registration SET registration_status='ACTIVE',failure_reason=NULL,dataset_version=?,"
                        + "risk_account_id=?,activated_at=?,updated_at=? "
                        + "WHERE registration_id=? AND registration_status IN ('PENDING','FAILED')",
                activeVersion,
                existing,
                now,
                now,
                registration.registrationId());
        jdbc.update(
                "INSERT INTO fds_risk_account_evidence "
                        + "(risk_account_id,registration_id,action_id,transfer_hold_id,investigation_id,actor_id,"
                        + "call_record_id,reason_code,policy_code,agent_report_json,evidence_transfer_ids_json,"
                        + "score_status,behavior_score,model_version,feature_version,dataset_version,registered_at,activated_at) "
                        + "SELECT ?,r.registration_id,r.action_id,r.transfer_hold_id,r.investigation_id,r.actor_id,"
                        + "r.call_record_id,r.reason_code,r.policy_code,rep.report_json,"
                        + "(SELECT JSON_ARRAYAGG(e.ref_id) FROM fds_investigation_evidence e "
                        + " WHERE e.investigation_id=r.investigation_id),r.score_status,r.behavior_score,"
                        + "r.model_version,r.feature_version,?,r.created_at,? FROM fds_risk_registration r "
                        + "LEFT JOIN fds_investigation_report rep ON rep.investigation_id=r.report_investigation_id "
                        + "WHERE r.registration_id=? ON DUPLICATE KEY UPDATE evidence_id=evidence_id",
                existing,
                activeVersion,
                now,
                registration.registrationId());
        return new Activation(registration.registrationId(), existing, activeVersion, false);
    }

    /** P4 거절 결과 대조 실패 등으로 위험 계좌 등록 의도를 FAILED 상태로 갱신한다. */
    @Transactional
    public void markRegistrationFailed(String actionId, String reason) {
        jdbc.update(
                "UPDATE fds_risk_registration SET registration_status='FAILED',failure_reason=?,updated_at=? "
                        + "WHERE action_id=? AND registration_status='PENDING'",
                reason,
                LocalDateTime.now(clock),
                actionId);
    }

    /** 활성 데이터셋과 관리자 사기 확정 위험 계좌 목록을 조회한다. */
    public List<RiskAccountSummary> findActiveAccounts() {
        // 등록과 근거를 계좌별로 집계한 뒤 조인하여 카테시안 곱 및 다중 행 분리를 방지한다.
        return jdbc.query(
                "SELECT r.risk_account_id,r.bank_code,r.account_number,r.source_type,r.created_at,"
                        + "COALESCE(reg.activated_at,d.activated_at,r.created_at),r.dataset_version,COALESCE(ev.evidence_count,0) "
                        + "FROM fds_risk_account r "
                        + "JOIN fds_risk_dataset d ON d.dataset_version=r.dataset_version "
                        + "LEFT JOIN (SELECT risk_account_id,MAX(activated_at) activated_at "
                        + "           FROM fds_risk_registration WHERE registration_status='ACTIVE' "
                        + "           GROUP BY risk_account_id) reg ON reg.risk_account_id=r.risk_account_id "
                        + "LEFT JOIN (SELECT risk_account_id,COUNT(*) evidence_count "
                        + "           FROM fds_risk_account_evidence GROUP BY risk_account_id) ev "
                        + "          ON ev.risk_account_id=r.risk_account_id "
                        + "WHERE r.source_type='ADMIN_CONFIRMED' OR r.dataset_version=(SELECT dataset_version FROM fds_risk_active_version WHERE singleton_id=1) "
                        + "ORDER BY COALESCE(reg.activated_at,d.activated_at,r.created_at) DESC,r.risk_account_id DESC",
                (rs, row) -> new RiskAccountSummary(
                        rs.getLong(1),
                        rs.getString(2),
                        mask(rs.getString(3)),
                        rs.getString(4),
                        rs.getTimestamp(5).toLocalDateTime(),
                        rs.getTimestamp(6).toLocalDateTime(),
                        rs.getString(7),
                        rs.getInt(8)));
    }

    /** 특정 위험 계좌의 요약 및 사건별 선정 근거 목록을 단건 조회한다. */
    public RiskAccountDetail findActiveAccount(long riskAccountId) {
        // 전체 목록 생성을 피하고 단건 쿼리로 위험 계좌 요약을 조회한다.
        List<RiskAccountSummary> rows = jdbc.query(
                "SELECT r.risk_account_id,r.bank_code,r.account_number,r.source_type,r.created_at,"
                        + "COALESCE(reg.activated_at,d.activated_at,r.created_at),r.dataset_version,COALESCE(ev.evidence_count,0) "
                        + "FROM fds_risk_account r "
                        + "JOIN fds_risk_dataset d ON d.dataset_version=r.dataset_version "
                        + "LEFT JOIN (SELECT risk_account_id,MAX(activated_at) activated_at "
                        + "           FROM fds_risk_registration WHERE registration_status='ACTIVE' "
                        + "           GROUP BY risk_account_id) reg ON reg.risk_account_id=r.risk_account_id "
                        + "LEFT JOIN (SELECT risk_account_id,COUNT(*) evidence_count "
                        + "           FROM fds_risk_account_evidence GROUP BY risk_account_id) ev "
                        + "          ON ev.risk_account_id=r.risk_account_id "
                        + "WHERE r.risk_account_id=?",
                (rs, row) -> new RiskAccountSummary(
                        rs.getLong(1),
                        rs.getString(2),
                        mask(rs.getString(3)),
                        rs.getString(4),
                        rs.getTimestamp(5).toLocalDateTime(),
                        rs.getTimestamp(6).toLocalDateTime(),
                        rs.getString(7),
                        rs.getInt(8)),
                riskAccountId);
        RiskAccountSummary summary =
                rows.stream().findFirst().orElseThrow(() -> new BusinessException(FdsErrorCode.NOT_FOUND));
        List<RiskEvidence> evidence = jdbc.query(
                "SELECT evidence_id,transfer_hold_id,action_id,investigation_id,actor_id,call_record_id,reason_code,"
                        + "policy_code,agent_report_json,evidence_transfer_ids_json,score_status,behavior_score,"
                        + "model_version,feature_version,dataset_version,activated_at "
                        + "FROM fds_risk_account_evidence WHERE risk_account_id=? ORDER BY activated_at,evidence_id",
                (rs, row) -> new RiskEvidence(
                        rs.getLong(1),
                        rs.getLong(2),
                        rs.getString(3),
                        rs.getString(4),
                        rs.getLong(5),
                        rs.getLong(6),
                        rs.getString(7),
                        rs.getString(8),
                        findings(rs.getString(9)),
                        evidenceIds(rs.getString(10)),
                        rs.getString(11),
                        rs.getBigDecimal(12) == null
                                ? null
                                : rs.getBigDecimal(12).doubleValue(),
                        rs.getString(13),
                        rs.getString(14),
                        rs.getString(15),
                        rs.getTimestamp(16).toLocalDateTime()),
                riskAccountId);
        return new RiskAccountDetail(summary, evidence);
    }

    private Registration registrationForUpdate(String actionId) {
        return jdbc.query(
                "SELECT registration_id,recipient_bank_code,recipient_account_number,registration_status,risk_account_id "
                        + "FROM fds_risk_registration WHERE action_id=? FOR UPDATE",
                rs -> rs.next()
                        ? new Registration(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), (Long)
                                rs.getObject(5))
                        : null,
                actionId);
    }

    private Long findExistingAdminRiskAccount(String bankCode, String accountNumber) {
        // 관리자 확정 출처의 위험 계좌만 조회하여 활성 데이터셋 SEED 행과의 혼동을 방지한다.
        return jdbc.query(
                "SELECT risk_account_id FROM fds_risk_account WHERE bank_code=? AND account_number=? "
                        + "AND source_type='ADMIN_CONFIRMED' LIMIT 1 FOR UPDATE",
                rs -> rs.next() ? rs.getLong(1) : null,
                bankCode,
                accountNumber);
    }

    private static String mask(String account) {
        return account.length() <= 4
                ? "****"
                : "*".repeat(account.length() - 4) + account.substring(account.length() - 4);
    }

    private static List<AgentFinding> findings(String reportJson) {
        if (reportJson == null) return List.of();
        try {
            List<AgentFinding> result = new ArrayList<>();
            for (JsonNode node : JSON.readTree(reportJson).path("secondaryFindings")) {
                result.add(new AgentFinding(
                        node.path("code").asText(),
                        node.path("description").asText(),
                        node.path("confidence").asText()));
            }
            return List.copyOf(result);
        } catch (RuntimeException exception) {
            throw new BusinessException(FdsErrorCode.INVALID_INVESTIGATION_REPORT, exception);
        }
    }

    private static List<Long> evidenceIds(String evidenceJson) {
        if (evidenceJson == null) return List.of();
        try {
            List<Long> result = new ArrayList<>();
            for (JsonNode node : JSON.readTree(evidenceJson)) result.add(node.asLong());
            return List.copyOf(result);
        } catch (RuntimeException exception) {
            throw new BusinessException(FdsErrorCode.INVALID_INVESTIGATION_REPORT, exception);
        }
    }

    /** 새 위험 데이터셋 버전을 검증 적재하고 활성 버전으로 전환한다. */
    @Transactional
    public void loadAndActivate(String version, int expectedCount, String sha256, List<RiskRow> rows) {
        if (version == null
                || version.isBlank()
                || expectedCount != rows.size()
                || sha256 == null
                || !sha256.matches("[0-9a-f]{64}")) {
            throw new BusinessException(FdsErrorCode.INVALID_RISK_DATASET);
        }
        long distinct = rows.stream()
                .map(row -> normalizeBank(row.bankCode()) + ':' + normalizeAccount(row.accountNumber()))
                .distinct()
                .count();
        if (distinct != rows.size()) throw new BusinessException(FdsErrorCode.INVALID_RISK_DATASET);
        LocalDateTime now = LocalDateTime.now(clock);
        // 적재와 관리자 판정 모두 활성 포인터를 먼저 잠가 계좌 행과의 교착 순서를 제거한다.
        jdbc.query("SELECT singleton_id FROM fds_risk_active_version WHERE singleton_id=1 FOR UPDATE", rs -> null);
        jdbc.update(
                "INSERT INTO fds_risk_dataset "
                        + "(dataset_version,dataset_status,expected_count,content_sha256,created_at) "
                        + "VALUES (?,'LOADED',?,?,?) ON DUPLICATE KEY UPDATE dataset_version=dataset_version",
                version,
                expectedCount,
                sha256,
                now);
        String storedHash = jdbc.queryForObject(
                "SELECT content_sha256 FROM fds_risk_dataset WHERE dataset_version=?", String.class, version);
        if (!sha256.equals(storedHash)) throw new BusinessException(FdsErrorCode.FLOW_CONFLICT);
        for (RiskRow row : rows) {
            jdbc.update(
                    "INSERT INTO fds_risk_account "
                            + "(dataset_version,bank_code,account_number,source_type,reason,created_at) "
                            + "VALUES (?,?,?,'SEED',?,?) ON DUPLICATE KEY UPDATE risk_account_id=risk_account_id",
                    version,
                    normalizeBank(row.bankCode()),
                    normalizeAccount(row.accountNumber()),
                    row.reason(),
                    now);
        }
        // 관리자 등록 행을 제외하고 원본 SEED 데이터셋 행 건수만 검증한다.
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM fds_risk_account WHERE dataset_version=? AND source_type='SEED'",
                Integer.class,
                version);
        if (count == null || count != expectedCount) throw new BusinessException(FdsErrorCode.INVALID_RISK_DATASET);
        jdbc.update(
                "INSERT INTO fds_risk_active_version(singleton_id,dataset_version,updated_at) VALUES(1,?,?) "
                        + "ON DUPLICATE KEY UPDATE dataset_version=VALUES(dataset_version),updated_at=VALUES(updated_at)",
                version,
                now);
        jdbc.update("UPDATE fds_risk_dataset SET dataset_status='LOADED' WHERE dataset_status='ACTIVE'");
        jdbc.update(
                "UPDATE fds_risk_dataset SET dataset_status='ACTIVE',activated_at=? WHERE dataset_version=?",
                now,
                version);
    }

    private static String normalizeBank(String value) {
        if (value == null || !value.matches("[0-9]{3}")) {
            throw new BusinessException(FdsErrorCode.INVALID_RISK_DATASET);
        }
        return value;
    }

    private static String normalizeAccount(String value) {
        if (value == null) throw new BusinessException(FdsErrorCode.INVALID_RISK_DATASET);
        String normalized = value.replaceAll("[^0-9]", "");
        if (!normalized.matches("[0-9]{8,20}")) {
            throw new BusinessException(FdsErrorCode.INVALID_RISK_DATASET);
        }
        return normalized;
    }

    public record RiskRow(String bankCode, String accountNumber, String reason) {}

    private record Registration(
            String registrationId, String bankCode, String accountNumber, String status, Long riskAccountId) {}

    public record Activation(String registrationId, long riskAccountId, String datasetVersion, boolean replay) {}

    public record RiskAccountSummary(
            long riskAccountId,
            String bankCode,
            String accountNumberMasked,
            String source,
            LocalDateTime registeredAt,
            LocalDateTime activatedAt,
            String datasetVersion,
            int evidenceCount) {}

    public record RiskEvidence(
            long evidenceId,
            long transferHoldId,
            String actionId,
            String investigationId,
            long actorId,
            long callRecordId,
            String reasonCode,
            String policyCode,
            List<AgentFinding> agentFindings,
            List<Long> evidenceTransferIds,
            String scoreStatus,
            Double score,
            String modelVersion,
            String featureVersion,
            String datasetVersion,
            LocalDateTime activatedAt) {}

    public record AgentFinding(String code, String description, String confidence) {}

    public record RiskAccountDetail(RiskAccountSummary account, List<RiskEvidence> evidence) {}

    public record Check(String status, Long riskAccountId, String datasetVersion, String unavailableReason) {
        static Check match(long id, String version) {
            return new Check("MATCH", id, version, null);
        }

        static Check noMatch(String version) {
            return new Check("NO_MATCH", null, version, null);
        }

        static Check unavailable(String version, String reason) {
            return new Check("UNAVAILABLE", null, version, reason);
        }
    }
}
