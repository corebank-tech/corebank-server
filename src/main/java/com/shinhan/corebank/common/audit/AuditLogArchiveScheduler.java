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

// 전날 감사 로그를 WORM 저장소에 올린다(PH-82).
@Component
@RequiredArgsConstructor
public class AuditLogArchiveScheduler {
    private static final Logger log = LoggerFactory.getLogger(AuditLogArchiveScheduler.class);
    private static final String JOB_NAME = "AUDIT_LOG_ARCHIVE";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final AuditLogArchiveService auditLogArchiveService;
    private final BatchExecutionLockPort batchExecutionLockPort;
    private final Clock clock;

    // 일일 이체 배치(00:10)와 정각·15·45분 정리 배치를 피해 00:30에 돈다.
    @Scheduled(cron = "0 30 0 * * *", zone = "Asia/Seoul")
    public void archiveYesterday() {
        if (!batchExecutionLockPort.tryAcquire(JOB_NAME)) {
            log.warn("이미 실행 중인 감사 로그 적재 배치가 있어 이번 트리거는 건너뜀 - jobName={}", JOB_NAME);
            return;
        }
        try {
            LocalDate yesterday = LocalDate.now(clock.withZone(SEOUL)).minusDays(1);
            auditLogArchiveService.archive(yesterday);
            log.info("감사 로그 적재 완료 - archiveDate={}", yesterday);
        } finally {
            batchExecutionLockPort.release(JOB_NAME);
        }
    }
}
