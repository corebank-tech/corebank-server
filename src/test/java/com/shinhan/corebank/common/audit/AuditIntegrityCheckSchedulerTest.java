package com.shinhan.corebank.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.batch.api.BatchExecutionLockPort;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

class AuditIntegrityCheckSchedulerTest extends IntegrationTestSupport {

    private static final String JOB_NAME = "AUDIT_INTEGRITY_CHECK";
    // 다른 감사 테스트(2030-01-15 · 16)와 사본 파일이 겹치지 않는 날
    private static final LocalDate EVENT_DATE = LocalDate.of(2030, 1, 17);
    // 정기 검증은 어제부터 7일을 보므로 실제 어제를 쓴다
    private static final LocalDate YESTERDAY =
            LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);
    private static final LocalDate TWO_DAYS_AGO = YESTERDAY.minusDays(1);

    @Autowired
    ApplicationEventPublisher eventPublisher;

    @Autowired
    AuditIntegrityCheckScheduler auditIntegrityCheckScheduler;

    @Autowired
    AuditLogArchiveService auditLogArchiveService;

    @Autowired
    BatchExecutionLockPort batchExecutionLockPort;

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
        for (LocalDate date : new LocalDate[] {EVENT_DATE, YESTERDAY, TWO_DAYS_AGO}) {
            for (AuditArchivePath path : AuditArchivePath.values()) {
                Files.deleteIfExists(archiveRoot.resolve(path.keyOf(date)));
            }
        }
    }

    @AfterEach
    void cleanUp() {
        auditIntegrityMismatchJpaRepository.deleteAll();
        auditArchiveExecutionJpaRepository.deleteAll();
        auditLogJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("적재 완료 이벤트가 오면 그날을 바로 검증해 불일치를 남긴다")
    void event_checksTheArchivedDay() {
        // given
        Long tamperedId = archiveAndTamper(EVENT_DATE);

        // when
        eventPublisher.publishEvent(new AuditArchiveCompletedEvent(EVENT_DATE));

        // then
        assertThat(auditIntegrityMismatchJpaRepository.findAll())
                .extracting(
                        AuditIntegrityMismatchJpaEntity::getAuditLogId,
                        AuditIntegrityMismatchJpaEntity::getMismatchType)
                .containsExactly(tuple(tamperedId, AuditIntegrityMismatchType.TAMPERED));
    }

    @Test
    @DisplayName("검증 락이 잡혀 있으면 이벤트는 검증을 건너뛰고, 락이 풀린 뒤 이벤트는 검증한다")
    void event_whileLockHeld_skipsThenChecksAfterRelease() {
        // given
        Long tamperedId = archiveAndTamper(EVENT_DATE);
        assertThat(batchExecutionLockPort.tryAcquire(JOB_NAME)).isTrue();

        try {
            // when
            eventPublisher.publishEvent(new AuditArchiveCompletedEvent(EVENT_DATE));

            // then
            assertThat(auditIntegrityMismatchJpaRepository.findAll()).isEmpty();
        } finally {
            batchExecutionLockPort.release(JOB_NAME);
        }

        eventPublisher.publishEvent(new AuditArchiveCompletedEvent(EVENT_DATE));

        assertThat(auditIntegrityMismatchJpaRepository.findAll())
                .extracting(AuditIntegrityMismatchJpaEntity::getAuditLogId)
                .containsExactly(tamperedId);
        assertThat(batchExecutionLockPort.isRunning(JOB_NAME)).isFalse();
    }

    @Test
    @DisplayName("정기 검증은 적재가 끝난 날만 대조한다")
    void cron_checksOnlyArchivedDays() {
        // given
        Long tamperedId = archiveAndTamper(YESTERDAY);

        // when
        auditIntegrityCheckScheduler.checkRecentDays();

        // then
        assertThat(auditIntegrityMismatchJpaRepository.findAll())
                .extracting(
                        AuditIntegrityMismatchJpaEntity::getAuditLogId,
                        AuditIntegrityMismatchJpaEntity::getMismatchType)
                .containsExactly(tuple(tamperedId, AuditIntegrityMismatchType.TAMPERED));
    }

    @Test
    @DisplayName("정기 검증은 적재가 끝나지 않은 날을 건너뛴다. 사본이 없는 날을 대조하면 전부 미적재로 오탐이 난다")
    void cron_skipsDayWhoseArchiveIsNotFinished() {
        // given
        save(YESTERDAY.atTime(9, 0));

        // when
        auditIntegrityCheckScheduler.checkRecentDays();

        // then
        assertThat(auditIntegrityMismatchJpaRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("정기 검증은 한 날짜의 검증이 실패해도 그보다 오래된 날짜를 계속 대조한다")
    void cron_whenOneDayFails_stillChecksOlderDays() throws IOException {
        // given
        save(YESTERDAY.atTime(9, 0));
        auditLogArchiveService.archive(YESTERDAY);
        Path brokenCopy = archiveRoot.resolve(AuditArchivePath.values()[0].keyOf(YESTERDAY));
        Files.writeString(brokenCopy, "not a json line");
        Long tamperedId = archiveAndTamper(TWO_DAYS_AGO);

        // when
        auditIntegrityCheckScheduler.checkRecentDays();

        // then
        assertThat(auditIntegrityMismatchJpaRepository.findAll())
                .extracting(AuditIntegrityMismatchJpaEntity::getAuditLogId)
                .containsExactly(tamperedId);
    }

    // 적재를 끝낸 뒤 그 행의 내용을 바꿔 사본과 달라지게 한다
    private Long archiveAndTamper(LocalDate archiveDate) {
        Long rowId = save(archiveDate.atTime(9, 0));
        auditLogArchiveService.archive(archiveDate);
        jdbcTemplate.update(
                "UPDATE audit_log SET detail = JSON_OBJECT('targetCustomerId', 99) WHERE audit_log_id = ?", rowId);
        return rowId;
    }

    private Long save(LocalDateTime requestedAt) {
        AuditLogJpaEntity auditLog = AuditLogJpaEntity.of(
                1L, null, AuditEventType.LOGIN, "10.0.0.7", true, Map.of("targetCustomerId", 2L), requestedAt);
        return auditLogJpaRepository.save(auditLog).getAuditLogId();
    }
}
