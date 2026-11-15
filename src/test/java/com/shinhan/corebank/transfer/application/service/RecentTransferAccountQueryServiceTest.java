package com.shinhan.corebank.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.transfer.application.port.in.RecentTransferAccountResult;
import com.shinhan.corebank.transfer.application.port.out.AccountLockPort;
import com.shinhan.corebank.transfer.application.port.out.LockedAccountStatus;
import com.shinhan.corebank.transfer.application.port.out.LockedAccountType;
import com.shinhan.corebank.transfer.application.port.out.RecentDepositAccount;
import com.shinhan.corebank.transfer.application.port.out.RecentTransferAccountQueryPort;
import com.shinhan.corebank.transfer.application.port.out.ResolvedPayee;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecentTransferAccountQueryServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 11, 9, 30);

    @Mock
    private RecentTransferAccountQueryPort queryPort;

    @Mock
    private AccountLockPort accountLockPort;

    private RecentTransferAccountQueryService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        service = new RecentTransferAccountQueryService(queryPort, accountLockPort, clock);
    }

    @Test
    @DisplayName("6개월 전부터 최신 5건을 조회하고, 지금 예금주명과 이체 가능 여부를 붙여 순서대로 돌려준다")
    void queryRecent_attachesCurrentPayeeAndTransferable() {
        LocalDateTime last1 = NOW.minusDays(1);
        LocalDateTime last2 = NOW.minusDays(2);
        when(queryPort.findRecentDepositAccounts(1L, NOW.minusMonths(6), 5))
                .thenReturn(List.of(
                        new RecentDepositAccount("110222222222", last1),
                        new RecentDepositAccount("110333333333", last2)));
        when(accountLockPort.resolvePayeesByAccountNumbers(List.of("110222222222", "110333333333")))
                .thenReturn(Map.of(
                        "110222222222", payee(202L, "홍길동", LockedAccountStatus.ACTIVE),
                        "110333333333", payee(203L, "김철수", LockedAccountStatus.SUSPENDED)));

        List<RecentTransferAccountResult> results = service.queryRecent(1L);

        assertThat(results)
                .containsExactly(
                        new RecentTransferAccountResult("110222222222", "홍길동", last1, true),
                        new RecentTransferAccountResult("110333333333", "김철수", last2, false));
    }

    @Test
    @DisplayName("계좌를 다시 찾을 수 없으면 보여 줄 예금주명이 없으므로 목록에서 뺀다")
    void queryRecent_dropsUnresolvableAccount() {
        when(queryPort.findRecentDepositAccounts(anyLong(), any(), anyInt()))
                .thenReturn(List.of(
                        new RecentDepositAccount("110222222222", NOW.minusDays(1)),
                        new RecentDepositAccount("110999999999", NOW.minusDays(2))));
        when(accountLockPort.resolvePayeesByAccountNumbers(List.of("110222222222", "110999999999")))
                .thenReturn(Map.of("110222222222", payee(202L, "홍길동", LockedAccountStatus.ACTIVE)));

        assertThat(service.queryRecent(1L))
                .extracting(RecentTransferAccountResult::depositAccountNumber)
                .containsExactly("110222222222");
    }

    @Test
    @DisplayName("최근 이체가 없으면 계좌 조회 없이 빈 목록이다")
    void queryRecent_withoutTransfers_returnsEmpty() {
        when(queryPort.findRecentDepositAccounts(anyLong(), any(), anyInt())).thenReturn(List.of());

        assertThat(service.queryRecent(1L)).isEmpty();
        verify(queryPort).findRecentDepositAccounts(1L, NOW.minusMonths(6), 5);
        verifyNoInteractions(accountLockPort);
    }

    private static ResolvedPayee payee(long accountId, String name, LockedAccountStatus status) {
        return new ResolvedPayee(accountId, name, LockedAccountType.DEMAND_DEPOSIT, status);
    }
}
