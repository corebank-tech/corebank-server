package com.shinhan.corebank.batch.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// tryAcquire()/release()가 REQUIRES_NEW로 각각 독립 커밋되는지 실제로 봐야 하므로
// 클래스 레벨 @Transactional을 쓰지 않는다(AutoTransferBatchItemProcessorTest와 동일한 이유).
// 어댑터를 직접 new하지 않고 스프링 빈으로 주입받는다 - @Transactional은 AOP 프록시로 동작해서
// 수동 생성하면 REQUIRES_NEW가 완전히 무시되고 @Lock(PESSIMISTIC_WRITE) 쿼리가
// TransactionRequiredException을 던진다.
class BatchExecutionLockPersistenceAdapterTest extends IntegrationTestSupport {

    @Autowired
    BatchExecutionLockJpaRepository batchExecutionLockJpaRepository;

    @Autowired
    BatchExecutionLockPersistenceAdapter adapter;

    @Autowired
    EntityManager entityManager;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    Clock clock;

    private static final AtomicLong JOB_SEQ = new AtomicLong();

    private String jobName;

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    void setUp() {
        jobName = "TEST_JOB_" + JOB_SEQ.incrementAndGet();
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
    @DisplayName("실행 중이 아니면 선점에 성공하고 currently_running을 실제로 TRUE로 커밋한다")
    void tryAcquire_notRunning_succeedsAndPersists() {
        boolean acquired = adapter.tryAcquire(jobName);

        assertThat(acquired).isTrue();
        BatchExecutionLockJpaEntity persisted = batchExecutionLockJpaRepository.findById(jobName).orElseThrow();
        assertThat(persisted.isCurrentlyRunning()).isTrue();
    }

    @Test
    @DisplayName("이미 실행 중이면 두 번째 선점 시도는 실패한다")
    void tryAcquire_alreadyRunning_returnsFalse() {
        adapter.tryAcquire(jobName);

        boolean secondAttempt = adapter.tryAcquire(jobName);

        assertThat(secondAttempt).isFalse();
    }

    @Test
    @DisplayName("release() 이후에는 다시 선점할 수 있다")
    void tryAcquire_afterRelease_succeedsAgain() {
        adapter.tryAcquire(jobName);

        adapter.release(jobName);
        boolean reacquired = adapter.tryAcquire(jobName);

        assertThat(reacquired).isTrue();
    }

    @Test
    @DisplayName("실행 중이어도 마지막 갱신이 stale 임계값(6시간)보다 오래됐으면 강제로 재획득한다")
    void tryAcquire_staleLock_forciblyReacquires() {
        adapter.tryAcquire(jobName);
        // 서버 크래시로 release()가 못 불린 상황을 흉내 - updated_at을 임계값보다 더 과거로 되돌림.
        // DB의 NOW() 대신 어댑터와 동일한 Clock 빈으로 계산한다 - DB 컨테이너와 JVM은 서로 다른
        // 프로세스의 시계라 6시간 경계값 근처에서 미세한 시계 오차로 흔들릴 수 있다(CI에서 실제로 발생).
        LocalDateTime sevenHoursAgo = LocalDateTime.now(clock).minusHours(7);
        transactionTemplate().executeWithoutResult(status ->
                entityManager.createNativeQuery(
                                "UPDATE batch_execution_lock SET updated_at = :updatedAt "
                                        + "WHERE job_name = :jobName")
                        .setParameter("updatedAt", sevenHoursAgo)
                        .setParameter("jobName", jobName)
                        .executeUpdate());

        boolean reacquired = adapter.tryAcquire(jobName);

        assertThat(reacquired).isTrue();
        BatchExecutionLockJpaEntity persisted = batchExecutionLockJpaRepository.findById(jobName).orElseThrow();
        assertThat(persisted.isCurrentlyRunning()).isTrue();
    }

    @Test
    @DisplayName("실행 중이고 갱신된 지 얼마 안 됐으면(stale 임계값 이내) 여전히 선점에 실패한다")
    void tryAcquire_recentlyRunning_stillReturnsFalse() {
        adapter.tryAcquire(jobName);
        LocalDateTime fiveHoursAgo = LocalDateTime.now(clock).minusHours(5);
        transactionTemplate().executeWithoutResult(status ->
                entityManager.createNativeQuery(
                                "UPDATE batch_execution_lock SET updated_at = :updatedAt "
                                        + "WHERE job_name = :jobName")
                        .setParameter("updatedAt", fiveHoursAgo)
                        .setParameter("jobName", jobName)
                        .executeUpdate());

        boolean secondAttempt = adapter.tryAcquire(jobName);

        assertThat(secondAttempt).isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 jobName으로 선점을 시도하면 예외를 던진다")
    void tryAcquire_unknownJobName_throwsIllegalState() {
        assertThatThrownBy(() -> adapter.tryAcquire("NO_SUCH_JOB"))
                .isInstanceOf(IllegalStateException.class);
    }
}
