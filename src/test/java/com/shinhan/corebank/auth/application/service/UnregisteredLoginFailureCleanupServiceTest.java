package com.shinhan.corebank.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.auth.application.port.out.UnregisteredLoginFailurePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UnregisteredLoginFailureCleanupServiceTest {

    @Mock
    UnregisteredLoginFailurePort unregisteredLoginFailurePort;

    @InjectMocks
    UnregisteredLoginFailureCleanupService service;

    @Test
    @DisplayName("한 번에 1,000건이 꽉 차게 지워지면 1,000건 미만이 될 때까지 반복하고 총 삭제 건수를 돌려준다")
    void deleteExpired_repeatsUntilFewerThanBatchSize() {
        // Given
        when(unregisteredLoginFailurePort.deleteExpired(1000)).thenReturn(1000, 1000, 3);

        // When
        final int totalDeleted = service.deleteExpired();

        // Then
        assertThat(totalDeleted).isEqualTo(2003);
        verify(unregisteredLoginFailurePort, times(3)).deleteExpired(1000);
    }

    @Test
    @DisplayName("지울 기록이 없으면 한 번만 조회하고 0을 돌려준다")
    void deleteExpired_nothingToDelete_runsOnce() {
        // Given
        when(unregisteredLoginFailurePort.deleteExpired(1000)).thenReturn(0);

        // When
        final int totalDeleted = service.deleteExpired();

        // Then
        assertThat(totalDeleted).isZero();
        verify(unregisteredLoginFailurePort, times(1)).deleteExpired(1000);
    }
}
