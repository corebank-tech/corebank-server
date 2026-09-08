package com.shinhan.corebank.common.idempotency;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.batch.application.port.out.BatchExecutionLockPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IdempotencyKeyCleanupSchedulerUnitTest {

    private static final String JOB_NAME = "IDEMPOTENCY_KEY_CLEANUP";

    @Mock
    IdempotencyKeyJpaRepository idempotencyKeyJpaRepository;

    @Mock
    BatchExecutionLockPort batchExecutionLockPort;

    private IdempotencyKeyCleanupScheduler scheduler() {
        return new IdempotencyKeyCleanupScheduler(idempotencyKeyJpaRepository, batchExecutionLockPort);
    }

    @Test
    @DisplayName("락 선점에 실패하면(이미 다른 인스턴스가 실행 중) 삭제를 시도하지 않고 release()도 부르지 않는다")
    void cleanupExpired_lockNotAcquired_skipsEverything() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(false);

        scheduler().cleanupExpired();

        verify(idempotencyKeyJpaRepository, never()).deleteExpiredBatch(any(), anyInt());
        verify(batchExecutionLockPort, never()).release(JOB_NAME);
    }

    @Test
    @DisplayName("한 번에 다 안 지워지면(딱 BATCH_SIZE만큼 지워짐) 0건 미만으로 지워질 때까지 반복해서 호출하고, 끝나면 락을 반납한다")
    void cleanupExpired_multipleRounds_repeatsUntilLessThanBatchSize() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);
        // 1000건 -> 1000건 -> 300건(마지막) 순으로 3라운드 도는 상황을 재현
        when(idempotencyKeyJpaRepository.deleteExpiredBatch(any(), anyInt())).thenReturn(1000, 1000, 300);

        scheduler().cleanupExpired();

        verify(idempotencyKeyJpaRepository, times(3)).deleteExpiredBatch(any(), anyInt());
        verify(batchExecutionLockPort).release(JOB_NAME);
    }

    @Test
    @DisplayName("삭제 도중 예외가 나도 락은 반드시 반납한다")
    void cleanupExpired_repositoryThrows_stillReleasesLock() {
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);
        when(idempotencyKeyJpaRepository.deleteExpiredBatch(any(), anyInt())).thenThrow(new RuntimeException("DB 장애"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> scheduler().cleanupExpired())
                .isInstanceOf(RuntimeException.class);

        verify(batchExecutionLockPort).release(JOB_NAME);
    }
}
