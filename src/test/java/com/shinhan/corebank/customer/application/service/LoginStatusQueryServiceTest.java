package com.shinhan.corebank.customer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.account.application.port.in.AccountGroupCode;
import com.shinhan.corebank.account.application.port.in.AccountOverviewQueryUseCase;
import com.shinhan.corebank.account.application.port.in.AccountOverviewResult;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.customer.application.port.in.LoginStatusResult;
import com.shinhan.corebank.customer.application.port.out.LoginHistoryQueryPort;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoginStatusQueryServiceTest {

    @Mock
    LoginHistoryQueryPort loginHistoryQueryPort;

    @Mock
    AccountOverviewQueryUseCase accountOverviewQueryUseCase;

    @InjectMocks
    LoginStatusQueryService service;

    @Test
    @DisplayName("직전 접속일시가 있고, 보유 계좌 중 가장 최근 거래일시를 골라 currentIp와 함께 반환한다")
    void getLoginStatus_combinesPreviousLoginAccountsAndCurrentIp() {
        LocalDateTime previousLoginAt = LocalDateTime.of(2026, 3, 5, 10, 0);
        when(loginHistoryQueryPort.findPreviousSuccessfulLogin(1L)).thenReturn(Optional.of(previousLoginAt));
        when(accountOverviewQueryUseCase.getOverview(1L)).thenReturn(overviewWithTransactionDates(
                LocalDateTime.of(2026, 3, 10, 9, 0),
                LocalDateTime.of(2026, 3, 12, 15, 0), // 가장 최근
                LocalDateTime.of(2026, 3, 1, 8, 0)));

        LoginStatusResult result = service.getLoginStatus(1L, "203.245.11.87");

        assertThat(result.previousLoginAt()).isEqualTo(previousLoginAt);
        assertThat(result.currentIp()).isEqualTo("203.245.11.87");
        assertThat(result.lastTransactionAt()).isEqualTo(LocalDateTime.of(2026, 3, 12, 15, 0));
    }

    @Test
    @DisplayName("직전 접속 기록이 없으면(첫 로그인) previousLoginAt은 null이다")
    void getLoginStatus_noPreviousLogin_previousLoginAtIsNull() {
        when(loginHistoryQueryPort.findPreviousSuccessfulLogin(2L)).thenReturn(Optional.empty());
        when(accountOverviewQueryUseCase.getOverview(2L)).thenReturn(overviewWithTransactionDates());

        LoginStatusResult result = service.getLoginStatus(2L, "1.1.1.1");

        assertThat(result.previousLoginAt()).isNull();
    }

    @Test
    @DisplayName("보유 계좌가 없거나 전부 거래 이력이 없으면 lastTransactionAt은 null이다")
    void getLoginStatus_noTransactions_lastTransactionAtIsNull() {
        when(loginHistoryQueryPort.findPreviousSuccessfulLogin(3L)).thenReturn(Optional.empty());
        when(accountOverviewQueryUseCase.getOverview(3L)).thenReturn(overviewWithTransactionDates((LocalDateTime) null));

        LoginStatusResult result = service.getLoginStatus(3L, "1.1.1.1");

        assertThat(result.lastTransactionAt()).isNull();
    }

    private AccountOverviewResult overviewWithTransactionDates(LocalDateTime... transactionDates) {
        // List.of()는 null 원소를 못 담아서(거래 이력 없는 계좌 테스트용) Arrays.asList()를 쓴다
        List<AccountOverviewResult.AccountItem> accounts = java.util.Arrays.asList(transactionDates).stream()
                .map(date -> new AccountOverviewResult.AccountItem(
                        1L, "계좌", "110000000001", AccountType.DEMAND_DEPOSIT, 10_000L,
                        AccountStatus.ACTIVE, LocalDate.of(2026, 1, 1), date, null, true))
                .toList();
        AccountOverviewResult.Group group = new AccountOverviewResult.Group(
                AccountGroupCode.DEMAND_DEPOSIT, "입출금계좌", 10_000L, accounts);
        return new AccountOverviewResult(OffsetDateTime.now(), 10_000L, List.of(group));
    }
}
