package com.shinhan.corebank.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.transfer.application.port.in.FavoriteAccountResult;
import com.shinhan.corebank.transfer.application.port.in.FavoriteAccountUpdateCommand;
import com.shinhan.corebank.transfer.application.port.out.AccountLockPort;
import com.shinhan.corebank.transfer.application.port.out.FavoriteAccountPersistencePort;
import com.shinhan.corebank.transfer.application.port.out.LockedAccountStatus;
import com.shinhan.corebank.transfer.application.port.out.LockedAccountType;
import com.shinhan.corebank.transfer.application.port.out.ResolvedPayee;
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
class FavoriteAccountUpdateServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 18, 10, 0, 0);

    @Mock
    private AccountLockPort accountLockPort;

    @Mock
    private FavoriteAccountPersistencePort persistencePort;

    private FavoriteAccountUpdateService service;

    @Test
    @DisplayName("본인 소유 항목의 별칭을 수정하면 변경된 결과를 반환한다")
    void update_success_returnsUpdatedResult() {
        service = new FavoriteAccountUpdateService(accountLockPort, persistencePort);
        FavoriteAccount existing = FavoriteAccount.of(10L, 1L, "110222222222", "홍길동", "엄마", NOW);
        when(persistencePort.findById(10L)).thenReturn(Optional.of(existing));
        when(persistencePort.save(any(FavoriteAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountLockPort.resolvePayeeByAccountNumber("110222222222"))
                .thenReturn(Optional.of(
                        new ResolvedPayee(202L, "홍길동", LockedAccountType.DEMAND_DEPOSIT, LockedAccountStatus.ACTIVE)));

        FavoriteAccountResult result = service.update(new FavoriteAccountUpdateCommand(10L, 1L, "우리엄마"));

        assertThat(result.alias()).isEqualTo("우리엄마");
        assertThat(result.transferable()).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 항목을 수정하려 하면 FAV0201을 던진다")
    void update_whenNotFound_throwsNotFound() {
        service = new FavoriteAccountUpdateService(accountLockPort, persistencePort);
        when(persistencePort.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(new FavoriteAccountUpdateCommand(999L, 1L, "우리엄마")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(FavoriteAccountErrorCode.FAVORITE_ACCOUNT_NOT_FOUND));
    }

    @Test
    @DisplayName("타인 소유 항목을 수정하려 하면 존재하지 않는 것과 동일하게 FAV0201을 던진다")
    void update_whenNotOwner_throwsNotFound() {
        service = new FavoriteAccountUpdateService(accountLockPort, persistencePort);
        FavoriteAccount existing = FavoriteAccount.of(10L, 2L, "110222222222", "홍길동", "엄마", NOW);
        when(persistencePort.findById(10L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(new FavoriteAccountUpdateCommand(10L, 1L, "우리엄마")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(FavoriteAccountErrorCode.FAVORITE_ACCOUNT_NOT_FOUND));
    }

    @Test
    @DisplayName("입금계좌가 더 이상 조회되지 않아도 별칭 수정은 성공하고 transferable은 false다")
    void update_whenPayeeNoLongerResolvable_stillSucceedsWithNotTransferable() {
        service = new FavoriteAccountUpdateService(accountLockPort, persistencePort);
        FavoriteAccount existing = FavoriteAccount.of(10L, 1L, "110222222222", "홍길동", "엄마", NOW);
        when(persistencePort.findById(10L)).thenReturn(Optional.of(existing));
        when(persistencePort.save(any(FavoriteAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountLockPort.resolvePayeeByAccountNumber("110222222222")).thenReturn(Optional.empty());

        FavoriteAccountResult result = service.update(new FavoriteAccountUpdateCommand(10L, 1L, "우리엄마"));

        assertThat(result.alias()).isEqualTo("우리엄마");
        assertThat(result.transferable()).isFalse();
    }
}
