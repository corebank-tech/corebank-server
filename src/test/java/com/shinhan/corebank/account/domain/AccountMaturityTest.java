package com.shinhan.corebank.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.account.domain.exception.AccountErrorCode;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountMaturityTest {

    private static final LocalDateTime OPENED_AT = LocalDateTime.of(2026, 9, 1, 10, 0);

    private static final LocalDate MATURITY_DATE = LocalDate.of(2026, 10, 26);

    private Account account(AccountStatus status, AccountType type) {
        return Account.reconstitute(
                101L,
                "088200000001",
                1L,
                type == AccountType.DEMAND_DEPOSIT ? null : 11L,
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
                type == AccountType.DEMAND_DEPOSIT ? null : MATURITY_DATE,
                status == AccountStatus.CLOSED ? OPENED_AT.plusDays(1) : null,
                null,
                0L,
                OPENED_AT,
                OPENED_AT);
    }

    @Test
    @DisplayName("만기일이 기준일과 같으면 MATURED로 전이한다")
    void markMatured_onMaturityDate() {
        Account account = account(AccountStatus.ACTIVE, AccountType.TIME_DEPOSIT);

        boolean changed = account.markMatured(MATURITY_DATE);

        assertThat(changed).isTrue();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.MATURED);
        assertThat(account.getBalance()).isEqualTo(1_000_000L);
        assertThat(account.getClosedDate()).isNull();
    }

    @Test
    @DisplayName("만기일이 기준일보다 이전이면 MATURED로 전이한다")
    void markMatured_overdue() {
        Account account = account(AccountStatus.ACTIVE, AccountType.INSTALLMENT_SAVINGS);

        boolean changed = account.markMatured(MATURITY_DATE.plusDays(1));

        assertThat(changed).isTrue();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.MATURED);
    }

    @Test
    @DisplayName("만기일이 아직 도래하지 않으면 전이하지 않는다")
    void markMatured_beforeMaturityDate() {
        Account account = account(AccountStatus.ACTIVE, AccountType.TIME_DEPOSIT);

        assertThatThrownBy(() -> account.markMatured(MATURITY_DATE.minusDays(1)))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_INPUT));

        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("이미 MATURED인 계좌는 재실행해도 상태가 변경되지 않는다")
    void markMatured_idempotent() {
        Account account = account(AccountStatus.ACTIVE, AccountType.TIME_DEPOSIT);

        assertThat(account.markMatured(MATURITY_DATE)).isTrue();
        assertThat(account.markMatured(MATURITY_DATE)).isFalse();

        assertThat(account.getStatus()).isEqualTo(AccountStatus.MATURED);
        assertThat(account.getBalance()).isEqualTo(1_000_000L);
    }

    @Test
    @DisplayName("요구불계좌는 만기 전이를 허용하지 않는다")
    void markMatured_demandDeposit() {
        Account account = account(AccountStatus.ACTIVE, AccountType.DEMAND_DEPOSIT);

        assertThatThrownBy(() -> account.markMatured(MATURITY_DATE)).isInstanceOf(BusinessException.class);

        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("ACTIVE 이외의 상태는 만기 전이를 허용하지 않는다")
    void markMatured_invalidStatus() {
        for (AccountStatus status :
                new AccountStatus[] {AccountStatus.SUSPENDED, AccountStatus.CLOSED, AccountStatus.DORMANT}) {
            Account account = account(status, AccountType.TIME_DEPOSIT);

            assertThatThrownBy(() -> account.markMatured(MATURITY_DATE))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                            .isEqualTo(AccountErrorCode.INVALID_ACCOUNT_STATUS));

            assertThat(account.getStatus()).isEqualTo(status);
        }
    }

    @Test
    @DisplayName("만기 판정 기준일이 null이면 예외가 발생한다")
    void markMatured_nullDate() {
        Account account = account(AccountStatus.ACTIVE, AccountType.TIME_DEPOSIT);

        assertThatThrownBy(() -> account.markMatured(null))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING));

        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }
}
