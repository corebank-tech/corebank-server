package com.shinhan.corebank.otp.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// 고객별 OTP 발급 잠금의 소유권·만료·즉시 커밋·동시 획득을 MySQL에서 검증한다.
class OtpIssueLockPersistenceAdapterTest extends IntegrationTestSupport {

    private static final Long CUSTOMER_ID = 958_003L;
    private static final Duration TTL = Duration.ofSeconds(10);

    @Autowired
    OtpIssueLockPersistenceAdapter adapter;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    Clock clock;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM otp_issue_lock WHERE customer_id = ?", CUSTOMER_ID);
    }

    @Test
    @DisplayName("같은 고객의 잠금은 한 소유자만 획득하고 다른 소유자는 해제하지 못한다")
    void onlyOwnerHoldsAndReleases() {
        Optional<String> owner = adapter.tryAcquire(CUSTOMER_ID, TTL);

        assertThat(owner).isPresent();
        assertThat(adapter.tryAcquire(CUSTOMER_ID, TTL)).isEmpty();

        adapter.release(CUSTOMER_ID, "someone-else");
        assertThat(adapter.tryAcquire(CUSTOMER_ID, TTL)).isEmpty();

        adapter.release(CUSTOMER_ID, owner.orElseThrow());
        assertThat(adapter.tryAcquire(CUSTOMER_ID, TTL)).isPresent();
    }

    @Test
    @DisplayName("만료된 잠금은 다른 요청이 다시 획득하고, 원래 소유자는 더 이상 해제하지 못한다")
    void expiredLockCanBeTakenOver() {
        String staleOwner = adapter.tryAcquire(CUSTOMER_ID, TTL).orElseThrow();
        jdbcTemplate.update(
                "UPDATE otp_issue_lock SET expires_at = ? WHERE customer_id = ?",
                LocalDateTime.now(clock).minusSeconds(1),
                CUSTOMER_ID);

        Optional<String> newOwner = adapter.tryAcquire(CUSTOMER_ID, TTL);
        assertThat(newOwner).isPresent().isNotEqualTo(Optional.of(staleOwner));

        adapter.release(CUSTOMER_ID, staleOwner);
        assertThat(adapter.tryAcquire(CUSTOMER_ID, TTL)).isEmpty();
    }

    @Test
    @DisplayName("호출자 트랜잭션이 롤백돼도 잠금은 이미 커밋돼 남아 있다")
    void lockCommitsIndependentlyOfCallerTransaction() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(adapter.tryAcquire(CUSTOMER_ID, TTL)).isPresent();
            status.setRollbackOnly();
        });

        assertThat(adapter.tryAcquire(CUSTOMER_ID, TTL)).isEmpty();
    }

    @Test
    @DisplayName("같은 고객이 동시에 10번 잠금을 시도하면 정확히 1건만 획득하고 교착 없이 끝난다")
    void concurrentAcquire_onlyOneSucceeds() throws Exception {
        int workers = 10;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Optional<String>>> results = new ArrayList<>();
            for (int i = 0; i < workers; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return adapter.tryAcquire(CUSTOMER_ID, TTL);
                }));
            }
            start.countDown();

            int acquired = 0;
            for (Future<Optional<String>> result : results) {
                if (result.get(30, TimeUnit.SECONDS).isPresent()) {
                    acquired++;
                }
            }
            assertThat(acquired).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }
}
