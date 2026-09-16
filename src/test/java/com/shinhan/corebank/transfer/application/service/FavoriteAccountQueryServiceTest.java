package com.shinhan.corebank.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.transfer.application.port.in.FavoriteAccountResult;
import com.shinhan.corebank.transfer.application.port.out.AccountLockPort;
import com.shinhan.corebank.transfer.application.port.out.FavoriteAccountPersistencePort;
import com.shinhan.corebank.transfer.application.port.out.LockedAccountStatus;
import com.shinhan.corebank.transfer.application.port.out.LockedAccountType;
import com.shinhan.corebank.transfer.application.port.out.ResolvedPayee;
import com.shinhan.corebank.transfer.domain.FavoriteAccount;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FavoriteAccountQueryServiceTest {

    @Mock
    private AccountLockPort accountLockPort;

    @Mock
    private FavoriteAccountPersistencePort persistencePort;

    private FavoriteAccountQueryService service;

    @Test
    @DisplayName("정상 계좌는 transferable=true, 해지/정지 계좌는 transferable=false로 매핑된다")
    void queryAll_mapsTransferableByCurrentAccountStatus() {
        service = new FavoriteAccountQueryService(accountLockPort, persistencePort);

        FavoriteAccount active =
                FavoriteAccount.of(1L, 1L, "110222222222", "홍길동", "엄마", LocalDateTime.of(2026, 8, 18, 10, 0, 0));
        FavoriteAccount closed =
                FavoriteAccount.of(2L, 1L, "110333333333", "김철수", "친구", LocalDateTime.of(2026, 8, 17, 10, 0, 0));

        when(persistencePort.findAllByCustomerId(1L)).thenReturn(List.of(active, closed));
        when(accountLockPort.resolvePayeesByAccountNumbers(List.of("110222222222", "110333333333")))
                .thenReturn(Map.of(
                        "110222222222",
                                new ResolvedPayee(
                                        202L, "홍길동", LockedAccountType.DEMAND_DEPOSIT, LockedAccountStatus.ACTIVE),
                        "110333333333",
                                new ResolvedPayee(
                                        203L, "김철수", LockedAccountType.DEMAND_DEPOSIT, LockedAccountStatus.CLOSED)));

        List<FavoriteAccountResult> results = service.queryAll(1L);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).transferable()).isTrue();
        assertThat(results.get(1).transferable()).isFalse();
    }

    @Test
    @DisplayName("계좌를 더 이상 찾을 수 없으면 안전하게 transferable=false로 처리한다")
    void queryAll_whenAccountNoLongerResolvable_defaultsToNotTransferable() {
        service = new FavoriteAccountQueryService(accountLockPort, persistencePort);

        FavoriteAccount favoriteAccount =
                FavoriteAccount.of(1L, 1L, "110222222222", "홍길동", "엄마", LocalDateTime.of(2026, 8, 18, 10, 0, 0));

        when(persistencePort.findAllByCustomerId(1L)).thenReturn(List.of(favoriteAccount));
        when(accountLockPort.resolvePayeesByAccountNumbers(List.of("110222222222")))
                .thenReturn(Map.of());

        List<FavoriteAccountResult> results = service.queryAll(1L);

        assertThat(results.get(0).transferable()).isFalse();
    }
}
