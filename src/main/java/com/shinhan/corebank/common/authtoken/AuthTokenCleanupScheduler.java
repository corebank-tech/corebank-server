package com.shinhan.corebank.common.authtoken;

import com.shinhan.corebank.batch.api.BatchExecutionLockPort;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 만료 토큰 행을 지워 공간을 회수한다. 유효성은 expires_at 조건이 판정하므로 이 배치가 멈춰도 보안은 그대로다.
@Component
@RequiredArgsConstructor
public class AuthTokenCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(AuthTokenCleanupScheduler.class);
    private static final String JOB_NAME = "AUTH_TOKEN_CLEANUP";
    private static final int BATCH_SIZE = 1000;

    private final AuthTokenJpaRepository authTokenJpaRepository;
    private final BatchExecutionLockPort batchExecutionLockPort;
    private final Clock clock;

    // 멱등키 정리(매시 정각)와 겹치지 않게 15분에 돈다.
    @Scheduled(cron = "0 15 * * * *", zone = "Asia/Seoul")
    public void cleanupExpired() {
        if (!batchExecutionLockPort.tryAcquire(JOB_NAME)) {
            log.warn("이미 실행 중인 정리 배치가 있어 이번 트리거는 건너뜀 - jobName={}", JOB_NAME);
            return;
        }
        try {
            int totalDeleted = 0;
            int deletedThisRound;
            do {
                deletedThisRound = authTokenJpaRepository.deleteExpiredBatch(LocalDateTime.now(clock), BATCH_SIZE);
                totalDeleted += deletedThisRound;
            } while (deletedThisRound == BATCH_SIZE);
            if (totalDeleted > 0) {
                log.info("만료된 인증 토큰 정리 완료 - deletedCount={}", totalDeleted);
            }
        } finally {
            batchExecutionLockPort.release(JOB_NAME);
        }
    }
}
