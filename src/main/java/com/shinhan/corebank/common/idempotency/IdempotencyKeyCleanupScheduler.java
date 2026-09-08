package com.shinhan.corebank.common.idempotency;

import com.shinhan.corebank.batch.api.BatchExecutionLockPort;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IdempotencyKeyCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyCleanupScheduler.class);
    private static final String JOB_NAME = "IDEMPOTENCY_KEY_CLEANUP";
    private static final int BATCH_SIZE = 1000;

    private final IdempotencyKeyJpaRepository idempotencyKeyJpaRepository;
    private final BatchExecutionLockPort batchExecutionLockPort;

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    public void cleanupExpired() {
        if (!batchExecutionLockPort.tryAcquire(JOB_NAME)) {
            log.warn("이미 실행 중인 정리 배치가 있어 이번 트리거는 건너뜀 - jobName={}", JOB_NAME);
            return;
        }
        try {
            int totalDeleted = 0;
            int deletedThisRound;
            do {
                deletedThisRound = idempotencyKeyJpaRepository.deleteExpiredBatch(LocalDateTime.now(), BATCH_SIZE);
                totalDeleted += deletedThisRound;
            } while (deletedThisRound == BATCH_SIZE);
            if (totalDeleted > 0) {
                log.info("만료된 멱등키 정리 완료 - deletedCount={}", totalDeleted);
            }
        } finally {
            batchExecutionLockPort.release(JOB_NAME);
        }
    }
}
