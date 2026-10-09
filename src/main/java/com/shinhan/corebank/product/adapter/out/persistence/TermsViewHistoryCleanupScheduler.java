package com.shinhan.corebank.product.adapter.out.persistence;

import com.shinhan.corebank.batch.api.BatchExecutionLockPort;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 만료 약관 열람 이력을 지운다. Redis TTL처럼 30분 지난 고객 열람 기록을 남기지 않는다. 유효성은 expires_at 조건이 판정한다.
@Component
@RequiredArgsConstructor
public class TermsViewHistoryCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(TermsViewHistoryCleanupScheduler.class);
    private static final String JOB_NAME = "TERMS_VIEW_HISTORY_CLEANUP";
    private static final int BATCH_SIZE = 1000;

    private final TermsViewHistoryJpaRepository termsViewHistoryJpaRepository;
    private final BatchExecutionLockPort batchExecutionLockPort;
    private final Clock clock;

    // 멱등키(정각)·인증 토큰(15분) 정리와 겹치지 않게 45분에 돈다.
    @Scheduled(cron = "0 45 * * * *", zone = "Asia/Seoul")
    public void cleanupExpired() {
        if (!batchExecutionLockPort.tryAcquire(JOB_NAME)) {
            log.warn("이미 실행 중인 정리 배치가 있어 이번 트리거는 건너뜀 - jobName={}", JOB_NAME);
            return;
        }
        try {
            int totalDeleted = 0;
            int deletedThisRound;
            do {
                deletedThisRound =
                        termsViewHistoryJpaRepository.deleteExpiredBatch(LocalDateTime.now(clock), BATCH_SIZE);
                totalDeleted += deletedThisRound;
            } while (deletedThisRound == BATCH_SIZE);
            if (totalDeleted > 0) {
                log.info("만료된 약관 열람 이력 정리 완료 - deletedCount={}", totalDeleted);
            }
        } finally {
            batchExecutionLockPort.release(JOB_NAME);
        }
    }
}
