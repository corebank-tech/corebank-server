package com.shinhan.corebank.fds.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.fds.domain.exception.FdsErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 활성 P6 데이터셋만 정본으로 위험 계좌를 판정하고 버전 전환을 직렬화한다. */
@Service
@RequiredArgsConstructor
public class FdsRiskAccountService {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public Check check(String bankCode, String accountNumber) {
        return check(bankCode, accountNumber, false);
    }

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
                            + "WHERE dataset_version=? AND bank_code=? AND account_number=?",
                    Long.class,
                    version,
                    normalizeBank(bankCode),
                    normalizeAccount(accountNumber));
            return ids.isEmpty() ? Check.noMatch(version) : Check.match(ids.getFirst(), version);
        } catch (DataAccessException exception) {
            return Check.unavailable(null, "RISK_LOOKUP_FAILED");
        }
    }

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
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM fds_risk_account WHERE dataset_version=?", Integer.class, version);
        if (count == null || count != expectedCount) throw new BusinessException(FdsErrorCode.INVALID_RISK_DATASET);
        // 단일 제어 행 잠금으로 조회 조치와 활성 버전 전환의 순서를 고정한다.
        jdbc.query("SELECT singleton_id FROM fds_risk_active_version WHERE singleton_id=1 FOR UPDATE", rs -> null);
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
