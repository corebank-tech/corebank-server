package com.shinhan.corebank.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.shinhan.corebank.IntegrationTestSupport;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

class AuditLogArchiveServiceTest extends IntegrationTestSupport {

    // 다른 테스트가 남기는 오늘 날짜 감사 행과 겹치지 않는 날
    private static final LocalDate ARCHIVE_DATE = LocalDate.of(2030, 1, 15);

    @Autowired
    AuditLogArchiveService auditLogArchiveService;

    @Autowired
    AuditLogJpaRepository auditLogJpaRepository;

    @Autowired
    AuditArchiveStorage auditArchiveStorage;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @AfterEach
    void cleanUp() {
        auditLogJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("그날 행만 두 경로로 나눠 올리고, 사유 있는 행은 access 경로에 담긴다. 해시 도입 전 행은 빠진다")
    void archive_splitsRowsOfTheDayByPath() {
        Long loginId = save(AuditEventType.LOGIN, null, ARCHIVE_DATE.atTime(9, 0));
        Long viewId = save(AuditEventType.CUSTOMER_DETAIL_VIEW, AccessReason.AUDIT, ARCHIVE_DATE.atTime(23, 59, 59));
        save(AuditEventType.LOGIN, null, ARCHIVE_DATE.plusDays(1).atStartOfDay());
        Long legacyId = save(AuditEventType.LOGIN, null, ARCHIVE_DATE.atTime(10, 0));
        jdbcTemplate.update("UPDATE audit_log SET row_hash = NULL WHERE audit_log_id = ?", legacyId);

        auditLogArchiveService.archive(ARCHIVE_DATE);

        assertThat(archivedLines("audit/log/2030/01/15.jsonl"))
                .extracting(AuditArchiveLine::auditLogId, AuditArchiveLine::rowHash)
                .containsExactly(tuple(loginId, rowHashOf(loginId)));
        assertThat(archivedLines("audit/access/2030/01/15.jsonl"))
                .extracting(AuditArchiveLine::auditLogId, AuditArchiveLine::rowHash)
                .containsExactly(tuple(viewId, rowHashOf(viewId)));
    }

    @Test
    @DisplayName("같은 날을 다시 올리면 내용이 같다")
    void archive_rerun_sameContent() {
        save(AuditEventType.CUSTOMER_SEARCH, AccessReason.OPERATION, ARCHIVE_DATE.atTime(12, 0));

        auditLogArchiveService.archive(ARCHIVE_DATE);
        byte[] first = auditArchiveStorage.get("audit/access/2030/01/15.jsonl").orElseThrow();
        auditLogArchiveService.archive(ARCHIVE_DATE);

        assertThat(auditArchiveStorage.get("audit/access/2030/01/15.jsonl")).hasValue(first);
    }

    private Long save(AuditEventType eventType, AccessReason accessReason, LocalDateTime requestedAt) {
        AuditLogJpaEntity auditLog = AuditLogJpaEntity.of(
                1L, null, eventType, "10.0.0.7", true, Map.of("targetCustomerId", 2L), accessReason, requestedAt);
        return auditLogJpaRepository.save(auditLog).getAuditLogId();
    }

    private String rowHashOf(Long auditLogId) {
        return jdbcTemplate.queryForObject(
                "SELECT row_hash FROM audit_log WHERE audit_log_id = ?", String.class, auditLogId);
    }

    private List<AuditArchiveLine> archivedLines(String key) {
        String jsonLines = new String(auditArchiveStorage.get(key).orElseThrow(), StandardCharsets.UTF_8);
        return jsonLines
                .lines()
                .map(line -> objectMapper.readValue(line, AuditArchiveLine.class))
                .toList();
    }
}
