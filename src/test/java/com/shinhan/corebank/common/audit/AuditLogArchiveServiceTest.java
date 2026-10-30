package com.shinhan.corebank.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.shinhan.corebank.IntegrationTestSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

class AuditLogArchiveServiceTest extends IntegrationTestSupport {

    // 다른 테스트가 남기는 오늘 날짜 감사 행과 겹치지 않는 날
    private static final LocalDate ARCHIVE_DATE = LocalDate.of(2030, 1, 15);
    private static final String LOG_KEY = "audit/log/2030/01/15.jsonl";
    private static final String ACCESS_KEY = "audit/access/2030/01/15.jsonl";

    @Autowired
    AuditLogArchiveService auditLogArchiveService;

    @Autowired
    AuditLogJpaRepository auditLogJpaRepository;

    @Autowired
    AuditArchiveStorage auditArchiveStorage;

    @Autowired
    AuditArchiveExecutionJpaRepository auditArchiveExecutionJpaRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    // 목 버킷은 디렉터리라 이전 실행의 사본이 남는다. 덮어쓰지 않으므로 매번 지우고 시작한다
    @Value("${app.audit.archive.local-dir:build/audit-archive}")
    Path archiveRoot;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void deleteArchivedCopies() throws IOException {
        Files.deleteIfExists(archiveRoot.resolve(LOG_KEY));
        Files.deleteIfExists(archiveRoot.resolve(ACCESS_KEY));
    }

    @AfterEach
    void cleanUp() {
        auditArchiveExecutionJpaRepository.deleteAll();
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

        assertThat(archivedLines(LOG_KEY))
                .extracting(AuditArchiveLine::auditLogId, AuditArchiveLine::rowHash)
                .containsExactly(tuple(loginId, rowHashOf(loginId)));
        assertThat(archivedLines(ACCESS_KEY))
                .extracting(AuditArchiveLine::auditLogId, AuditArchiveLine::rowHash)
                .containsExactly(tuple(viewId, rowHashOf(viewId)));
    }

    @Test
    @DisplayName("사본의 줄은 id가 아니라 요청 시각 순서다")
    void archive_linesOrderedByRequestedAt() {
        Long laterId = save(AuditEventType.LOGIN, null, ARCHIVE_DATE.atTime(10, 0));
        Long earlierId = save(AuditEventType.LOGIN, null, ARCHIVE_DATE.atTime(9, 0));

        auditLogArchiveService.archive(ARCHIVE_DATE);

        assertThat(archivedLines(LOG_KEY))
                .extracting(AuditArchiveLine::auditLogId)
                .containsExactly(earlierId, laterId);
    }

    @Test
    @DisplayName("성공한 경로는 상태가 SUCCESS · 행 수로 남고, 같은 날을 다시 돌려도 다시 올리지 않는다")
    void archive_rerun_skipsSucceededPaths() {
        save(AuditEventType.CUSTOMER_SEARCH, AccessReason.OPERATION, ARCHIVE_DATE.atTime(12, 0));
        auditLogArchiveService.archive(ARCHIVE_DATE);
        byte[] first = auditArchiveStorage.get(ACCESS_KEY).orElseThrow();
        save(AuditEventType.CUSTOMER_SEARCH, AccessReason.OPERATION, ARCHIVE_DATE.atTime(13, 0));

        auditLogArchiveService.archive(ARCHIVE_DATE);

        assertThat(auditArchiveStorage.get(ACCESS_KEY)).hasValue(first);
        assertThat(auditArchiveExecutionJpaRepository.findAll())
                .extracting(
                        AuditArchiveExecutionJpaEntity::getArchiveType,
                        AuditArchiveExecutionJpaEntity::getStatus,
                        AuditArchiveExecutionJpaEntity::getRowCount)
                .containsExactlyInAnyOrder(
                        tuple(AuditArchivePath.LOG, AuditArchiveStatus.SUCCESS, 0),
                        tuple(AuditArchivePath.ACCESS, AuditArchiveStatus.SUCCESS, 1));
    }

    @Test
    @DisplayName("업로드 뒤 상태를 못 남긴 날을 다시 돌리면, 사본과 같은 경로는 SUCCESS, 행이 지워진 경로는 원본을 두고 FAILURE")
    void archive_existingCopyDiffers_keepsOriginalAndFails() {
        Long deletedId = save(AuditEventType.LOGIN, null, ARCHIVE_DATE.atTime(9, 0));
        auditLogArchiveService.archive(ARCHIVE_DATE);
        byte[] original = auditArchiveStorage.get(LOG_KEY).orElseThrow();
        auditArchiveExecutionJpaRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM audit_log WHERE audit_log_id = ?", deletedId);

        assertThatThrownBy(() -> auditLogArchiveService.archive(ARCHIVE_DATE))
                .isInstanceOf(IllegalStateException.class);

        assertThat(auditArchiveStorage.get(LOG_KEY)).hasValue(original);
        assertThat(auditArchiveExecutionJpaRepository
                        .findByArchiveDateAndArchiveType(ARCHIVE_DATE, AuditArchivePath.LOG)
                        .orElseThrow())
                .extracting(AuditArchiveExecutionJpaEntity::getStatus, AuditArchiveExecutionJpaEntity::getFailureReason)
                .containsExactly(
                        AuditArchiveStatus.FAILURE, "IllegalStateException: 이미 올린 사본과 내용이 다릅니다 - key=" + LOG_KEY);
        assertThat(auditArchiveExecutionJpaRepository
                        .findByArchiveDateAndArchiveType(ARCHIVE_DATE, AuditArchivePath.ACCESS)
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(AuditArchiveStatus.SUCCESS);
    }

    @Test
    @DisplayName("log 경로가 실패해도 access 경로는 확정되고, 다음 실행이 실패한 log 경로만 다시 올린다")
    void archive_partialFailure_recoversOnlyFailedPath() throws IOException {
        Long loginId = save(AuditEventType.LOGIN, null, ARCHIVE_DATE.atTime(9, 0));
        Long viewId = save(AuditEventType.CUSTOMER_DETAIL_VIEW, AccessReason.AUDIT, ARCHIVE_DATE.atTime(10, 0));
        Path blocked = archiveRoot.resolve(LOG_KEY);
        Files.createDirectories(blocked.getParent());
        Files.writeString(blocked, "다른 내용");

        assertThatThrownBy(() -> auditLogArchiveService.archive(ARCHIVE_DATE))
                .isInstanceOf(IllegalStateException.class);

        assertThat(archivedLines(ACCESS_KEY))
                .extracting(AuditArchiveLine::auditLogId)
                .containsExactly(viewId);
        assertThat(auditArchiveExecutionJpaRepository.findAll())
                .extracting(AuditArchiveExecutionJpaEntity::getArchiveType, AuditArchiveExecutionJpaEntity::getStatus)
                .containsExactlyInAnyOrder(
                        tuple(AuditArchivePath.LOG, AuditArchiveStatus.FAILURE),
                        tuple(AuditArchivePath.ACCESS, AuditArchiveStatus.SUCCESS));

        Files.delete(blocked);
        auditLogArchiveService.archive(ARCHIVE_DATE);

        assertThat(archivedLines(LOG_KEY))
                .extracting(AuditArchiveLine::auditLogId)
                .containsExactly(loginId);
        assertThat(auditArchiveExecutionJpaRepository.findAll())
                .extracting(AuditArchiveExecutionJpaEntity::getStatus)
                .containsOnly(AuditArchiveStatus.SUCCESS);
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
