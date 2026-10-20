package com.shinhan.corebank.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountMaturityTransitionServiceTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 10, 26);

    private static final LocalDateTime OPENED_AT = LocalDateTime.of(2026, 9, 1, 10, 0);

    @Mock
    AccountPersistencePort accountPersistencePort;

    @InjectMocks
    AccountMaturityTransitionService service;

    @Test
    @DisplayName("ACTIVE 만기 계좌만 전이하고 다른 상태는 저장하지 않는다")
    void transition_onlyEligibleAccounts() {
        Account due = account(101L, AccountStatus.ACTIVE, AccountType.TIME_DEPOSIT, BASE_DATE);

        Account alreadyMatured = account(102L, AccountStatus.MATURED, AccountType.TIME_DEPOSIT, BASE_DATE);

        Account future = account(103L, AccountStatus.ACTIVE, AccountType.TIME_DEPOSIT, BASE_DATE.plusDays(1));

        Account suspended = account(104L, AccountStatus.SUSPENDED, AccountType.TIME_DEPOSIT, BASE_DATE);

        when(accountPersistencePort.findByAccountIdForUpdate(101L)).thenReturn(Optional.of(due));
        when(accountPersistencePort.findByAccountIdForUpdate(102L)).thenReturn(Optional.of(alreadyMatured));
        when(accountPersistencePort.findByAccountIdForUpdate(103L)).thenReturn(Optional.of(future));
        when(accountPersistencePort.findByAccountIdForUpdate(104L)).thenReturn(Optional.of(suspended));

        int count = service.transition(List.of(101L, 102L, 103L, 104L), BASE_DATE);

        assertThat(count).isEqualTo(1);
        assertThat(due.getStatus()).isEqualTo(AccountStatus.MATURED);
        assertThat(alreadyMatured.getStatus()).isEqualTo(AccountStatus.MATURED);
        assertThat(future.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(suspended.getStatus()).isEqualTo(AccountStatus.SUSPENDED);

        verify(accountPersistencePort).save(due);
        verify(accountPersistencePort, times(1)).save(any(Account.class));
    }

    @Test
    @DisplayName("이미 MATURED인 계좌를 다시 조회해도 중복 저장하지 않는다")
    void transition_idempotent() {
        Account active = account(101L, AccountStatus.ACTIVE, AccountType.TIME_DEPOSIT, BASE_DATE);

        Account matured = account(101L, AccountStatus.MATURED, AccountType.TIME_DEPOSIT, BASE_DATE);

        when(accountPersistencePort.findByAccountIdForUpdate(101L))
                .thenReturn(Optional.of(active), Optional.of(matured));

        assertThat(service.transition(List.of(101L), BASE_DATE)).isEqualTo(1);

        assertThat(service.transition(List.of(101L), BASE_DATE)).isZero();

        verify(accountPersistencePort, times(1)).save(active);
        verify(accountPersistencePort, times(2)).findByAccountIdForUpdate(101L);
    }

    @Test
    @DisplayName("잠금 조회 중 계좌가 사라지면 예외를 전파한다")
    void transition_missingAccount() {
        when(accountPersistencePort.findByAccountIdForUpdate(101L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transition(List.of(101L), BASE_DATE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INTERNAL_ERROR);

        verify(accountPersistencePort, never()).save(any(Account.class));
    }

    private Account account(Long accountId, AccountStatus status, AccountType type, LocalDate maturityDate) {

        return Account.reconstitute(
                accountId,
                "088200000101",
                1L,
                11L,
                type,
                1_000_000L,
                0L,
                status,
                "test-password-hash",
                0,
                false,
                null,
                null,
                false,
                null,
                OPENED_AT,
                maturityDate,
                null,
                null,
                0L,
                OPENED_AT,
                OPENED_AT);
    }
}
