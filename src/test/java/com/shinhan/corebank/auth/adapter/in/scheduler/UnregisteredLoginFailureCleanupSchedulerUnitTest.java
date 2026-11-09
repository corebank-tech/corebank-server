package com.shinhan.corebank.auth.adapter.in.scheduler;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.auth.application.port.in.UnregisteredLoginFailureCleanupUseCase;
import com.shinhan.corebank.batch.api.BatchExecutionLockPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UnregisteredLoginFailureCleanupSchedulerUnitTest {

    private static final String JOB_NAME = "UNREGISTERED_LOGIN_FAILURE_CLEANUP";

    @Mock
    UnregisteredLoginFailureCleanupUseCase unregisteredLoginFailureCleanupUseCase;

    @Mock
    BatchExecutionLockPort batchExecutionLockPort;

    @InjectMocks
    UnregisteredLoginFailureCleanupScheduler scheduler;

    @Test
    @DisplayName("락을 못 잡으면(다른 인스턴스가 실행 중) 삭제를 시도하지 않고 락도 반납하지 않는다")
    void cleanupExpired_lockNotAcquired_skipsEverything() {
        // Given
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(false);

        // When
        scheduler.cleanupExpired();

        // Then
        verify(unregisteredLoginFailureCleanupUseCase, never()).deleteExpired();
        verify(batchExecutionLockPort, never()).release(JOB_NAME);
    }

    @Test
    @DisplayName("삭제 도중 예외가 나도 락은 반드시 반납한다")
    void cleanupExpired_deleteThrows_stillReleasesLock() {
        // Given
        when(batchExecutionLockPort.tryAcquire(JOB_NAME)).thenReturn(true);
        when(unregisteredLoginFailureCleanupUseCase.deleteExpired()).thenThrow(new RuntimeException("DB 장애"));

        // When & Then
        assertThatThrownBy(() -> scheduler.cleanupExpired()).isInstanceOf(RuntimeException.class);
        verify(batchExecutionLockPort).release(JOB_NAME);
    }
}
