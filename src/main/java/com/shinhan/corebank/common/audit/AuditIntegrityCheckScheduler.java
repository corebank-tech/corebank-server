package com.shinhan.corebank.common.audit;

import com.shinhan.corebank.batch.api.BatchExecutionLockPort;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 감사 로그를 WORM 사본과 대조한다(PH-82). 적재가 끝나는 순간 그날을 바로 대조하고(이벤트),
// 01:00에는 최근 7일 중 적재가 끝난 날을 다시 훑어 이벤트를 놓친 날을 메운다. 하루만 보면 그 전날 이전에 고친 행을 놓친다.
// 적재가 SUCCESS가 아닌 날은 사본이 비어 있어 전부 NOT_ARCHIVED 오탐이 나므로 대조하지 않는다.
@Component
@RequiredArgsConstructor
public class AuditIntegrityCheckScheduler {
    private static final Logger log = LoggerFactory.getLogger(AuditIntegrityCheckScheduler.class);
    private static final String JOB_NAME = "AUDIT_INTEGRITY_CHECK";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int CHECK_DAYS = 7;

    private final AuditIntegrityCheckService auditIntegrityCheckService;
    private final BatchExecutionLockPort batchExecutionLockPort;
    private final Clock clock;

    // 적재 스케줄러 스레드에서 이어 도는 동안 적재 쪽으로 예외가 새지 않게 여기서 로그로 남긴다
    @EventListener
    public void checkJustArchived(AuditArchiveCompletedEvent event) {
        runWithLock(() -> checkAndReport(event.archiveDate()));
    }

    // 적재(00:30)가 아직 안 끝났거나 이벤트를 놓친 날을 위한 안전망이다
    @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Seoul")
    public void checkRecentDays() {
        runWithLock(this::checkArchivedDays);
    }

    private void checkArchivedDays() {
        LocalDate today = LocalDate.now(clock.withZone(SEOUL));
        for (int daysAgo = 1; daysAgo <= CHECK_DAYS; daysAgo++) {
            LocalDate archiveDate = today.minusDays(daysAgo);
            if (!auditIntegrityCheckService.isArchiveCompleted(archiveDate)) {
                log.warn("감사 로그 적재가 끝나지 않아 검증을 미룸 - archiveDate={}", archiveDate);
                continue;
            }
            checkAndReport(archiveDate);
        }
    }

    // 한 날짜의 실패가 다른 날짜의 검증을 막지 않도록 날짜마다 격리한다
    private void checkAndReport(LocalDate archiveDate) {
        try {
            int mismatchCount = auditIntegrityCheckService.check(archiveDate);
            if (mismatchCount > 0) {
                log.error("감사 로그 무결성 불일치 발견 - archiveDate={}, count={}", archiveDate, mismatchCount);
            }
        } catch (RuntimeException e) {
            log.error("감사 로그 무결성 검증 실패 - archiveDate={}", archiveDate, e);
        }
    }

    // 락 획득·해제 실패가 이벤트를 발행한 적재 쪽으로 새면 적재 성공이 「적재 실패」로 기록된다
    private void runWithLock(Runnable check) {
        try {
            runIfLockAcquired(check);
        } catch (RuntimeException e) {
            log.error("감사 로그 무결성 검증 락 처리 실패 - jobName={}", JOB_NAME, e);
        }
    }

    private void runIfLockAcquired(Runnable check) {
        if (!batchExecutionLockPort.tryAcquire(JOB_NAME)) {
            log.warn("이미 실행 중인 감사 로그 검증 배치가 있어 이번 트리거는 건너뜀 - jobName={}", JOB_NAME);
            return;
        }
        try {
            check.run();
        } catch (RuntimeException e) {
            log.error("감사 로그 무결성 검증 실패", e);
        } finally {
            batchExecutionLockPort.release(JOB_NAME);
        }
    }
}
