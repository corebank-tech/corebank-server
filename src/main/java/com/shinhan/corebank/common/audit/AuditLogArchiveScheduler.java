package com.shinhan.corebank.common.audit;

import com.shinhan.corebank.batch.api.BatchExecutionLockPort;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 최근 7일 중 적재가 끝나지 않은 날의 감사 로그를 WORM 저장소에 올린다(PH-82). 실패 · 정지로 빠진 날도 다음 실행이 채운다(#788 리뷰).
// 한 날이 실패해도 다른 날은 계속 올린다. 업로드 실패는 audit_archive_execution에 사유와 함께, 조회 실패는 PENDING으로 남고
// 둘 다 여기서 ERROR로 알린다. 7일을 넘긴 미완료 날은 자동으로 다시 올리지 않는다.
@Component
@RequiredArgsConstructor
public class AuditLogArchiveScheduler {
    private static final Logger log = LoggerFactory.getLogger(AuditLogArchiveScheduler.class);
    private static final String JOB_NAME = "AUDIT_LOG_ARCHIVE";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int RECOVERY_DAYS = 7;

    private final AuditLogArchiveService auditLogArchiveService;
    private final BatchExecutionLockPort batchExecutionLockPort;
    private final Clock clock;

    // 일일 이체 배치(00:10)와 정각·15·45분 정리 배치를 피해 00:30에 돈다.
    @Scheduled(cron = "0 30 0 * * *", zone = "Asia/Seoul")
    public void archiveRecentDays() {
        if (!batchExecutionLockPort.tryAcquire(JOB_NAME)) {
            log.warn("이미 실행 중인 감사 로그 적재 배치가 있어 이번 트리거는 건너뜀 - jobName={}", JOB_NAME);
            return;
        }
        try {
            LocalDate today = LocalDate.now(clock.withZone(SEOUL));
            for (int daysAgo = RECOVERY_DAYS; daysAgo >= 1; daysAgo--) {
                archive(today.minusDays(daysAgo));
            }
        } finally {
            batchExecutionLockPort.release(JOB_NAME);
        }
    }

    private void archive(LocalDate archiveDate) {
        try {
            auditLogArchiveService.archive(archiveDate);
        } catch (RuntimeException e) {
            log.error("감사 로그 적재 실패 - archiveDate={}", archiveDate, e);
        }
    }
}
