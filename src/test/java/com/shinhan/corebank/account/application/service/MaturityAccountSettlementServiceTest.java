package com.shinhan.corebank.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.application.port.out.AccountStatusHistoryPort;
import com.shinhan.corebank.account.application.port.out.MaturityAccountSnapshot;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.account.domain.exception.AccountErrorCode;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MaturityAccountSettlementServiceTest {

    private static final Long ACCOUNT_ID = 100L;
    private static final Long CUSTOMER_ID = 1L;
    private static final LocalDate MATURITY_DATE = LocalDate.of(2026, 10, 22);
    private static final LocalDate TRADE_DATE = LocalDate.of(2026, 10, 23);
    private static final LocalDateTime CLOSED_AT = LocalDateTime.of(2026, 10, 23, 12, 0);

    @Mock
    private AccountPersistencePort accountPersistencePort;

    @Mock
    private AccountStatusHistoryPort accountStatusHistoryPort;

    @InjectMocks
    private MaturityAccountSettlementService service;

    @Test
    void requireCurrentBalance_success() {
        stubMaturityAccount();

        when(accountPersistencePort.findBalancesByAccountIds(List.of(ACCOUNT_ID)))
                .thenReturn(Map.of(ACCOUNT_ID, 1_000_000L));

        assertThat(service.requireCurrentBalance(ACCOUNT_ID, CUSTOMER_ID)).isEqualTo(1_000_000L);
    }

    @Test
    void requireCurrentBalance_accountNotFound() {
        when(accountPersistencePort.findMaturityAccountSnapshot(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(Optional.empty());

        assertAccountNotFound(() -> service.requireCurrentBalance(ACCOUNT_ID, CUSTOMER_ID));

        verify(accountPersistencePort, never()).findBalancesByAccountIds(any());
    }

    @Test
    void requireCurrentBalance_balanceNotFound() {
        stubMaturityAccount();

        when(accountPersistencePort.findBalancesByAccountIds(List.of(ACCOUNT_ID)))
                .thenReturn(Map.of());

        assertThatThrownBy(() -> service.requireCurrentBalance(ACCOUNT_ID, CUSTOMER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INTERNAL_ERROR);
    }

    @Test
    void closeMatured_success() {
        Account account = mock(Account.class);

        when(accountPersistencePort.findByAccountIdAndCustomerIdForUpdate(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(Optional.of(account));
        when(account.getMaturityDate()).thenReturn(MATURITY_DATE);

        service.closeMatured(ACCOUNT_ID, CUSTOMER_ID, CLOSED_AT, TRADE_DATE);

        verify(account).closeMatured(CLOSED_AT, TRADE_DATE);
        verify(accountPersistencePort).save(account);
        verify(accountStatusHistoryPort).recordMaturityClosure(ACCOUNT_ID, MATURITY_DATE, TRADE_DATE, CLOSED_AT);
    }

    @Test
    void closeMatured_accountNotFound() {
        when(accountPersistencePort.findByAccountIdAndCustomerIdForUpdate(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(Optional.empty());

        assertAccountNotFound(() -> service.closeMatured(ACCOUNT_ID, CUSTOMER_ID, CLOSED_AT, TRADE_DATE));

        verify(accountPersistencePort, never()).save(any(Account.class));
        verifyNoInteractions(accountStatusHistoryPort);
    }

    private void stubMaturityAccount() {
        when(accountPersistencePort.findMaturityAccountSnapshot(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(Optional.of(new MaturityAccountSnapshot(
                        ACCOUNT_ID, 10L, AccountType.TIME_DEPOSIT, AccountStatus.MATURED, MATURITY_DATE, false)));
    }

    private static void assertAccountNotFound(org.junit.jupiter.api.function.Executable action) {
        assertThatThrownBy(action::execute)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND_OR_FORBIDDEN);
    }
}
