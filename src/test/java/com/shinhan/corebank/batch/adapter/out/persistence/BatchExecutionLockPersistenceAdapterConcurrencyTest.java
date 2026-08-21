package com.shinhan.corebank.batch.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PESSIMISTIC_WRITE 행 락으로 tryAcquire()의 상호배제를 보장한다는 설계를, 순차 호출이 아니라
 * 실제 스레드 경합으로 증명한다. BatchExecutionLockPersistenceAdapterTest는 상태 전이만
 * 순차적으로 검증하고 동시 호출은 다루지 않는다 (PR #272 리뷰 후속, astrokan).
 */
class BatchExecutionLockPersistenceAdapterConcurrencyTest extends IntegrationTestSupport {

    private static final int CONCURRENT_ATTEMPTS = 20;

    @Autowired
    BatchExecutionLockJpaRepository batchExecutionLockJpaRepository;

    @Autowired
    BatchExecutionLockPersistenceAdapter adapter;

    @Autowired
    EntityManager entityManager;

    @Autowired
    PlatformTransactionManager transactionManager;

    private String jobName;

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    void setUp() {
        jobName = "CONCURRENCY_TEST_JOB";
        transactionTemplate().executeWithoutResult(status ->
                entityManager.createNativeQuery(
                                "INSERT INTO batch_execution_lock (job_name, currently_running, updated_at) "
                                        + "VALUES (:jobName, FALSE, NOW())")
                        .setParameter("jobName", jobName)
                        .executeUpdate());
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate().executeWithoutResult(status ->
                entityManager.createNativeQuery("DELETE FROM batch_execution_lock WHERE job_name = :jobName")
                        .setParameter("jobName", jobName)
                        .executeUpdate());
    }

    @Test
    @DisplayName("같은 jobName으로 여러 스레드가 동시에 tryAcquire해도 정확히 하나만 성공한다")
    void tryAcquire_concurrentAttempts_onlyOneSucceeds() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_ATTEMPTS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < CONCURRENT_ATTEMPTS; i++) {
                futures.add(pool.submit(callable(start)));
            }

            start.countDown();
            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }

            long succeeded = results.stream().filter(Boolean::booleanValue).count();
            assertThat(succeeded).isEqualTo(1);

            BatchExecutionLockJpaEntity persisted = batchExecutionLockJpaRepository.findById(jobName).orElseThrow();
            assertThat(persisted.isCurrentlyRunning()).isTrue();
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private Callable<Boolean> callable(CountDownLatch start) {
        return () -> {
            await(start);
            return adapter.tryAcquire(jobName);
        };
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
