package com.shinhan.corebank.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.shinhan.corebank.IntegrationTestSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

class AuditIntegrityCheckServiceTest extends IntegrationTestSupport {

    // 적재 테스트(2030-01-15)와 사본 파일이 겹치지 않는 날
    private static final LocalDate ARCHIVE_DATE = LocalDate.of(2030, 1, 16);

    @Autowired
    AuditIntegrityCheckService auditIntegrityCheckService;

    @Autowired
    AuditLogArchiveService auditLogArchiveService;

    @Autowired
    AuditLogJpaRepository auditLogJpaRepository;

    @Autowired
    AuditIntegrityMismatchJpaRepository auditIntegrityMismatchJpaRepository;

    @Autowired
    AuditArchiveExecutionJpaRepository auditArchiveExecutionJpaRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    // 목 버킷은 디렉터리라 이전 실행의 사본이 남는다. 덮어쓰지 않으므로 매번 지우고 시작한다
    @Value("${app.audit.archive.local-dir:build/audit-archive}")
    Path archiveRoot;

    @BeforeEach
    void deleteArchivedCopies() throws IOException {
        for (AuditArchivePath path : AuditArchivePath.values()) {
            Files.deleteIfExists(archiveRoot.resolve(path.keyOf(ARCHIVE_DATE)));
        }
    }

    @AfterEach
    void cleanUp() {
        auditIntegrityMismatchJpaRepository.deleteAll();
        auditArchiveExecutionJpaRepository.deleteAll();
        auditLogJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("적재 뒤 detail 수정은 TAMPERED, 행 삭제는 MISSING_IN_DB, 적재 뒤 끼워 넣은 행은 NOT_ARCHIVED로 남는다")
    void check_detectsTamperedMissingAndNotArchivedRows() {
        // given
        Long tamperedId = save(AuditEventType.LOGIN, ARCHIVE_DATE.atTime(9, 0));
        Long deletedId = savePersonalDataAccess(
                PersonalDataAccessEventType.CUSTOMER_DETAIL_VIEW, AccessReason.AUDIT, ARCHIVE_DATE.atTime(10, 0));
        save(AuditEventType.LOGIN, ARCHIVE_DATE.atTime(11, 0));
        auditLogArchiveService.archive(ARCHIVE_DATE);
        jdbcTemplate.update(
                "UPDATE audit_log SET detail = JSON_OBJECT('targetCustomerId', 99) WHERE audit_log_id = ?", tamperedId);
        jdbcTemplate.update("DELETE FROM audit_log WHERE audit_log_id = ?", deletedId);
        Long insertedId = save(AuditEventType.LOGIN, ARCHIVE_DATE.atTime(12, 0));

        // when
        int mismatchCount = auditIntegrityCheckService.check(ARCHIVE_DATE);

        // then
        assertThat(mismatchCount).isEqualTo(3);
        assertThat(auditIntegrityMismatchJpaRepository.findAll())
                .extracting(
                        AuditIntegrityMismatchJpaEntity::getArchiveDate,
                        AuditIntegrityMismatchJpaEntity::getAuditLogId,
                        AuditIntegrityMismatchJpaEntity::getMismatchType)
                .containsExactlyInAnyOrder(
                        tuple(ARCHIVE_DATE, tamperedId, AuditIntegrityMismatchType.TAMPERED),
                        tuple(ARCHIVE_DATE, deletedId, AuditIntegrityMismatchType.MISSING_IN_DB),
                        tuple(ARCHIVE_DATE, insertedId, AuditIntegrityMismatchType.NOT_ARCHIVED));
    }

    @Test
    @DisplayName("detail과 row_hash를 함께 고쳐도 사본 해시와 달라 TAMPERED로 잡힌다")
    void check_rowHashRewrittenTogether_stillTampered() {
        // given
        Long tamperedId = save(AuditEventType.LOGIN, ARCHIVE_DATE.atTime(9, 0));
        auditLogArchiveService.archive(ARCHIVE_DATE);
        AuditLogJpaEntity forged = AuditLogJpaEntity.of(
                1L,
                null,
                AuditEventType.LOGIN,
                "10.0.0.7",
                true,
                Map.of("targetCustomerId", 99L),
                ARCHIVE_DATE.atTime(9, 0));
        jdbcTemplate.update(
                "UPDATE audit_log SET detail = JSON_OBJECT('targetCustomerId', 99), row_hash = ? WHERE audit_log_id = ?",
                forged.getRowHash(),
                tamperedId);

        // when
        auditIntegrityCheckService.check(ARCHIVE_DATE);

        // then
        assertThat(auditIntegrityMismatchJpaRepository.findAll())
                .extracting(
                        AuditIntegrityMismatchJpaEntity::getAuditLogId,
                        AuditIntegrityMismatchJpaEntity::getMismatchType)
                .containsExactly(tuple(tamperedId, AuditIntegrityMismatchType.TAMPERED));
    }

    @Test
    @DisplayName("바뀐 게 없으면 불일치가 0건이다")
    void check_unchangedRows_noMismatch() {
        // given
        savePersonalDataAccess(
                PersonalDataAccessEventType.CUSTOMER_SEARCH, AccessReason.OPERATION, ARCHIVE_DATE.atTime(9, 0));
        save(AuditEventType.LOGIN, ARCHIVE_DATE.atTime(10, 0));
        auditLogArchiveService.archive(ARCHIVE_DATE);

        // when
        int mismatchCount = auditIntegrityCheckService.check(ARCHIVE_DATE);

        // then
        assertThat(mismatchCount).isZero();
        assertThat(auditIntegrityMismatchJpaRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("row_hash를 NULL로 넣은 위조 행은 NOT_ARCHIVED로 잡히고, 내용이 그대로인 행은 row_hash만 지워도 불일치가 아니다")
    void check_rowHashNull_forgedRowNotArchived() {
        // given
        Long intactId = save(AuditEventType.LOGIN, ARCHIVE_DATE.atTime(9, 0));
        auditLogArchiveService.archive(ARCHIVE_DATE);
        Long forgedId = save(AuditEventType.LOGIN, ARCHIVE_DATE.atTime(10, 0));
        jdbcTemplate.update("UPDATE audit_log SET row_hash = NULL WHERE audit_log_id IN (?, ?)", intactId, forgedId);

        // when
        auditIntegrityCheckService.check(ARCHIVE_DATE);

        // then
        assertThat(auditIntegrityMismatchJpaRepository.findAll())
                .extracting(
                        AuditIntegrityMismatchJpaEntity::getAuditLogId,
                        AuditIntegrityMismatchJpaEntity::getMismatchType)
                .containsExactly(tuple(forgedId, AuditIntegrityMismatchType.NOT_ARCHIVED));
    }

    @Test
    @DisplayName("해시 도입 전에 생긴 해시 없는 행은 검증에서 빠진다")
    void check_rowBeforeHashIntroduced_isNotReported() {
        // given
        LocalDate legacyDate = LocalDate.of(2026, 10, 20);
        Long legacyId = save(AuditEventType.LOGIN, legacyDate.atTime(9, 0));
        jdbcTemplate.update("UPDATE audit_log SET row_hash = NULL WHERE audit_log_id = ?", legacyId);

        // when
        int mismatchCount = auditIntegrityCheckService.check(legacyDate);

        // then
        assertThat(mismatchCount).isZero();
        assertThat(auditIntegrityMismatchJpaRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("사본이 아예 없는 날은 그날 행이 전부 NOT_ARCHIVED로 남는다")
    void check_noArchiveObject_allRowsNotArchived() {
        // given — 이 날은 적재하지 않는다
        LocalDate neverArchivedDate = LocalDate.of(2030, 1, 17);
        Long rowId = save(AuditEventType.LOGIN, neverArchivedDate.atTime(9, 0));

        // when
        auditIntegrityCheckService.check(neverArchivedDate);

        // then
        assertThat(auditIntegrityMismatchJpaRepository.findAll())
                .extracting(
                        AuditIntegrityMismatchJpaEntity::getAuditLogId,
                        AuditIntegrityMismatchJpaEntity::getMismatchType)
                .containsExactly(tuple(rowId, AuditIntegrityMismatchType.NOT_ARCHIVED));
    }

    @Test
    @DisplayName("같은 날을 다시 검증해도 이미 남긴 불일치는 지우거나 중복하지 않고, 새로 찾은 것만 더한다")
    void check_rerun_keepsRecordedAndAddsNewOnly() {
        // given
        Long tamperedId = save(AuditEventType.LOGIN, ARCHIVE_DATE.atTime(9, 0));
        auditLogArchiveService.archive(ARCHIVE_DATE);
        jdbcTemplate.update(
                "UPDATE audit_log SET detail = JSON_OBJECT('targetCustomerId', 99) WHERE audit_log_id = ?", tamperedId);
        auditIntegrityCheckService.check(ARCHIVE_DATE);
        LocalDateTime firstDetectedAt =
                auditIntegrityMismatchJpaRepository.findAll().get(0).getDetectedAt();
        auditIntegrityCheckService.check(ARCHIVE_DATE);
        jdbcTemplate.update("DELETE FROM audit_log WHERE audit_log_id = ?", tamperedId);

        // when
        auditIntegrityCheckService.check(ARCHIVE_DATE);

        // then
        assertThat(auditIntegrityMismatchJpaRepository.findAll())
                .extracting(
                        AuditIntegrityMismatchJpaEntity::getAuditLogId,
                        AuditIntegrityMismatchJpaEntity::getMismatchType)
                .containsExactlyInAnyOrder(
                        tuple(tamperedId, AuditIntegrityMismatchType.TAMPERED),
                        tuple(tamperedId, AuditIntegrityMismatchType.MISSING_IN_DB));
        assertThat(auditIntegrityMismatchJpaRepository.findAll())
                .filteredOn(mismatch -> mismatch.getMismatchType() == AuditIntegrityMismatchType.TAMPERED)
                .extracting(AuditIntegrityMismatchJpaEntity::getDetectedAt)
                .containsExactly(firstDetectedAt);
    }

    @Test
    @DisplayName("변조를 되돌려 놓아도 이미 남긴 탐지 기록은 사라지지 않는다")
    void check_revertedTamper_keepsDetectionRecord() {
        // given
        Long tamperedId = save(AuditEventType.LOGIN, ARCHIVE_DATE.atTime(9, 0));
        auditLogArchiveService.archive(ARCHIVE_DATE);
        String originalDetail = jdbcTemplate.queryForObject(
                "SELECT detail FROM audit_log WHERE audit_log_id = ?", String.class, tamperedId);
        jdbcTemplate.update(
                "UPDATE audit_log SET detail = JSON_OBJECT('targetCustomerId', 99) WHERE audit_log_id = ?", tamperedId);
        auditIntegrityCheckService.check(ARCHIVE_DATE);
        jdbcTemplate.update("UPDATE audit_log SET detail = ? WHERE audit_log_id = ?", originalDetail, tamperedId);

        // when
        int mismatchCount = auditIntegrityCheckService.check(ARCHIVE_DATE);

        // then
        assertThat(mismatchCount).isZero();
        assertThat(auditIntegrityMismatchJpaRepository.findAll())
                .extracting(
                        AuditIntegrityMismatchJpaEntity::getAuditLogId,
                        AuditIntegrityMismatchJpaEntity::getMismatchType)
                .containsExactly(tuple(tamperedId, AuditIntegrityMismatchType.TAMPERED));
    }

    @Test
    @DisplayName("모든 경로 적재가 SUCCESS인 날만 적재 완료로 본다")
    void isArchiveCompleted_trueOnlyWhenEveryPathSucceeded() {
        // given
        save(AuditEventType.LOGIN, ARCHIVE_DATE.atTime(9, 0));
        boolean beforeArchive = auditIntegrityCheckService.isArchiveCompleted(ARCHIVE_DATE);

        // when
        auditLogArchiveService.archive(ARCHIVE_DATE);

        // then
        assertThat(beforeArchive).isFalse();
        assertThat(auditIntegrityCheckService.isArchiveCompleted(ARCHIVE_DATE)).isTrue();
    }

    private Long save(AuditEventType eventType, LocalDateTime requestedAt) {
        AuditLogJpaEntity auditLog = AuditLogJpaEntity.of(
                1L, null, eventType, "10.0.0.7", true, Map.of("targetCustomerId", 2L), requestedAt);
        return auditLogJpaRepository.save(auditLog).getAuditLogId();
    }

    private Long savePersonalDataAccess(
            PersonalDataAccessEventType eventType, AccessReason accessReason, LocalDateTime requestedAt) {
        AuditLogJpaEntity auditLog = AuditLogJpaEntity.personalDataAccess(
                1L, eventType, "10.0.0.7", true, Map.of("targetCustomerId", 2L), accessReason, requestedAt);
        return auditLogJpaRepository.save(auditLog).getAuditLogId();
    }
}
