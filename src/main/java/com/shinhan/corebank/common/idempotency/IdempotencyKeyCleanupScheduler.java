package com.shinhan.corebank.common.idempotency;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class IdempotencyKeyCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyCleanupScheduler.class);
    private final IdempotencyKeyJpaRepository idempotencyKeyJpaRepository;

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    @Transactional
    public void cleanupExpired() {
        int deleted = idempotencyKeyJpaRepository.deleteAllByExpiresAtBefore(LocalDateTime.now());
        if (deleted > 0) {
            log.info("만료된 멱등키 정리 완료 - deletedCount={}", deleted);
        }
    }
}
