package com.shinhan.corebank.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class IdempotencyKeyCleanupSchedulerTest extends IntegrationTestSupport {

    @Autowired
    IdempotencyKeyCleanupScheduler scheduler;

    @Autowired
    IdempotencyKeyJpaRepository repository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate().executeWithoutResult(status -> repository.deleteAll());
    }

    @Test
    @DisplayName("만료 시각이 지난 멱등키는 삭제되고, 아직 안 지난 멱등키는 남는다")
    void cleanupExpired_deletesOnlyExpiredKeys() {
        String expiredKey = "11111111-1111-4111-8111-111111111111";
        String freshKey = "22222222-2222-4222-8222-222222222222";

        transactionTemplate().executeWithoutResult(status -> {
            repository.save(
                    IdempotencyKeyJpaEntity.start(expiredKey, null, "POST /test", "a".repeat(64), LocalDateTime.now()));
            repository.save(
                    IdempotencyKeyJpaEntity.start(freshKey, null, "POST /test", "b".repeat(64), LocalDateTime.now()));

            // start()는 항상 now+24시간으로 expiresAt을 정하므로, expiredKey만 이미 지난 시각으로 되돌려 재현한다
            entityManager
                    .createNativeQuery("UPDATE idempotency_key SET expires_at = :t WHERE idempotency_key = :k")
                    .setParameter("t", LocalDateTime.now().minusHours(1))
                    .setParameter("k", expiredKey)
                    .executeUpdate();
        });

        scheduler.cleanupExpired();

        assertThat(repository.findById(expiredKey)).isEmpty();
        assertThat(repository.findById(freshKey)).isPresent();
    }

    @Test
    @DisplayName("만료된 멱등키가 없으면 아무것도 지우지 않는다")
    void cleanupExpired_nothingExpired_deletesNothing() {
        String freshKey = "33333333-3333-4333-8333-333333333333";
        transactionTemplate()
                .executeWithoutResult(status -> repository.save(IdempotencyKeyJpaEntity.start(
                        freshKey, null, "POST /test", "c".repeat(64), LocalDateTime.now())));

        scheduler.cleanupExpired();

        assertThat(repository.findById(freshKey)).isPresent();
    }
}
