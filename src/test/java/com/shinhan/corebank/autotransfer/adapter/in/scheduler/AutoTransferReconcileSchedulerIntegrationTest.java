package com.shinhan.corebank.autotransfer.adapter.in.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferBatchUseCase;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// 락 포트를 모킹하지 않고 실제 batch_execution_lock 테이블로 스케줄러를 호출한다.
// 락 행 마이그레이션이 빠지면 첫 테스트가 실패한다(PR #635 리뷰 R1).
// 클래스 레벨 @Transactional을 쓰지 않는다 - 락의 tryAcquire/release는 REQUIRES_NEW로 따로 커밋된다.
class AutoTransferReconcileSchedulerIntegrationTest extends IntegrationTestSupport {

    private static final String RECONCILE_JOB = "AUTO_TRANSFER_RECONCILE";
    private static final String MAIN_JOB = "DAILY_TRANSFER_BATCH";

    @MockitoBean
    AutoTransferBatchUseCase autoTransferBatchUseCase;

    @Autowired
    AutoTransferReconcileScheduler scheduler;

    @Autowired
    EntityManager entityManager;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    Clock clock;

    @AfterEach
    void restoreLocks() {
        setLock(MAIN_JOB, false, LocalDateTime.now(clock));
        setLock(RECONCILE_JOB, false, LocalDateTime.now(clock));
        Mockito.reset(autoTransferBatchUseCase);
    }

    @Test
    @DisplayName("마이그레이션이 재확정 락 행을 만든다")
    void migrationCreatesReconcileLockRow() {
        assertThat(lockRowCount(RECONCILE_JOB)).isEqualTo(1L);
    }

    @Test
    @DisplayName("메인 배치가 안 돌면 재확정을 실행하고 자기 락을 반납한다")
    void reconcile_mainBatchIdle_runsAndReleasesLock() {
        scheduler.reconcile();

        verify(autoTransferBatchUseCase).reconcileStuckExecutions(any());
        assertThat(isRunning(RECONCILE_JOB)).isFalse();
    }

    @Test
    @DisplayName("메인 배치가 방금 시작되어 실행 중이면 재확정을 건너뛴다")
    void reconcile_mainBatchRunning_skips() {
        setLock(MAIN_JOB, true, LocalDateTime.now(clock));

        scheduler.reconcile();

        verify(autoTransferBatchUseCase, never()).reconcileStuckExecutions(any());
        assertThat(isRunning(RECONCILE_JOB)).isFalse();
    }

    @Test
    @DisplayName("메인 배치 락이 크래시로 6시간 넘게 실행 중으로 남아 있으면 건너뛰지 않고 재확정한다")
    void reconcile_staleMainBatchLock_stillReconciles() {
        setLock(MAIN_JOB, true, LocalDateTime.now(clock).minusHours(7));

        scheduler.reconcile();

        verify(autoTransferBatchUseCase).reconcileStuckExecutions(any());
        assertThat(isRunning(RECONCILE_JOB)).isFalse();
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private void setLock(String jobName, boolean running, LocalDateTime updatedAt) {
        transaction().executeWithoutResult(status -> entityManager
                .createNativeQuery("UPDATE batch_execution_lock SET currently_running = :running,"
                        + " updated_at = :updatedAt WHERE job_name = :jobName")
                .setParameter("running", running)
                .setParameter("updatedAt", updatedAt)
                .setParameter("jobName", jobName)
                .executeUpdate());
    }

    private long lockRowCount(String jobName) {
        return ((Number) transaction().execute(status -> entityManager
                        .createNativeQuery("SELECT COUNT(*) FROM batch_execution_lock WHERE job_name = :jobName")
                        .setParameter("jobName", jobName)
                        .getSingleResult()))
                .longValue();
    }

    private boolean isRunning(String jobName) {
        Object value = transaction().execute(status -> entityManager
                .createNativeQuery("SELECT currently_running FROM batch_execution_lock WHERE job_name = :jobName")
                .setParameter("jobName", jobName)
                .getSingleResult());
        // 드라이버에 따라 Boolean 또는 숫자로 돌아온다.
        return value instanceof Boolean flag ? flag : ((Number) value).intValue() != 0;
    }
}
