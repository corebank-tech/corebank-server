package com.shinhan.corebank.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.transfer.application.port.in.FavoriteAccountDeleteCommand;
import com.shinhan.corebank.transfer.application.port.out.FavoriteAccountPersistencePort;
import com.shinhan.corebank.transfer.domain.FavoriteAccount;
import com.shinhan.corebank.transfer.domain.exception.FavoriteAccountErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FavoriteAccountDeleteServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 18, 10, 0, 0);

    @Mock
    private FavoriteAccountPersistencePort persistencePort;

    private FavoriteAccountDeleteService service;

    @Test
    @DisplayName("본인 소유 항목을 삭제하면 persistencePort.deleteById가 호출된다")
    void delete_success_deletesRecord() {
        service = new FavoriteAccountDeleteService(persistencePort);
        FavoriteAccount existing = FavoriteAccount.of(10L, 1L, "110222222222", "홍길동", "엄마", NOW);
        when(persistencePort.findById(10L)).thenReturn(Optional.of(existing));

        service.delete(new FavoriteAccountDeleteCommand(10L, 1L));

        verify(persistencePort).deleteById(10L);
    }

    @Test
    @DisplayName("존재하지 않는 항목을 삭제하려 하면 FAV0201을 던지고 삭제를 호출하지 않는다")
    void delete_whenNotFound_throwsNotFound() {
        service = new FavoriteAccountDeleteService(persistencePort);
        when(persistencePort.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(new FavoriteAccountDeleteCommand(999L, 1L)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(FavoriteAccountErrorCode.FAVORITE_ACCOUNT_NOT_FOUND));
        verify(persistencePort, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("타인 소유 항목을 삭제하려 하면 존재하지 않는 것과 동일하게 FAV0201을 던진다")
    void delete_whenNotOwner_throwsNotFound() {
        service = new FavoriteAccountDeleteService(persistencePort);
        FavoriteAccount existing = FavoriteAccount.of(10L, 2L, "110222222222", "홍길동", "엄마", NOW);
        when(persistencePort.findById(10L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.delete(new FavoriteAccountDeleteCommand(10L, 1L)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(FavoriteAccountErrorCode.FAVORITE_ACCOUNT_NOT_FOUND));
        verify(persistencePort, never()).deleteById(anyLong());
    }
}
