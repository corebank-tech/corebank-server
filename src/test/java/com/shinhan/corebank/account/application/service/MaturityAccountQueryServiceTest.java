package com.shinhan.corebank.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.application.port.out.MaturityAccountSnapshot;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.account.domain.exception.AccountErrorCode;
import com.shinhan.corebank.common.exception.BusinessException;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MaturityAccountQueryServiceTest {

    private static final Long ACCOUNT_ID = 100L;
    private static final Long CUSTOMER_ID = 1L;
    private static final LocalDate MATURITY_DATE = LocalDate.of(2026, 10, 22);

    @Mock
    private AccountPersistencePort accountPersistencePort;

    @InjectMocks
    private MaturityAccountQueryService service;

    @Test
    void requireMatured_success() {
        stub(new MaturityAccountSnapshot(
                ACCOUNT_ID, 10L, AccountType.TIME_DEPOSIT, AccountStatus.MATURED, MATURITY_DATE, false));

        var result = service.requireMatured(ACCOUNT_ID, CUSTOMER_ID, MATURITY_DATE);

        assertThat(result.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(result.productId()).isEqualTo(10L);
        assertThat(result.maturityDate()).isEqualTo(MATURITY_DATE);
        assertThat(result.timeDeposit()).isTrue();
    }

    @Test
    void requireMatured_rejectsActive() {
        stub(new MaturityAccountSnapshot(
                ACCOUNT_ID, 10L, AccountType.TIME_DEPOSIT, AccountStatus.ACTIVE, MATURITY_DATE, false));

        assertError(
                () -> service.requireMatured(ACCOUNT_ID, CUSTOMER_ID, MATURITY_DATE),
                AccountErrorCode.INVALID_ACCOUNT_STATUS);
    }

    @Test
    void requireMatured_rejectsBeforeMaturityDate() {
        stub(new MaturityAccountSnapshot(
                ACCOUNT_ID, 10L, AccountType.TIME_DEPOSIT, AccountStatus.MATURED, MATURITY_DATE.plusDays(1), false));

        assertError(
                () -> service.requireMatured(ACCOUNT_ID, CUSTOMER_ID, MATURITY_DATE),
                AccountErrorCode.MATURITY_DATE_NOT_REACHED);
    }

    @Test
    void requireMatured_rejectsDemandDeposit() {
        stub(new MaturityAccountSnapshot(
                ACCOUNT_ID, null, AccountType.DEMAND_DEPOSIT, AccountStatus.MATURED, MATURITY_DATE, false));

        assertError(
                () -> service.requireMatured(ACCOUNT_ID, CUSTOMER_ID, MATURITY_DATE),
                AccountErrorCode.MATURITY_UNSUPPORTED_ACCOUNT_TYPE);
    }

    @Test
    void requireWithdrawalDestination_rejectsInactiveAccount() {
        stub(new MaturityAccountSnapshot(
                ACCOUNT_ID, null, AccountType.DEMAND_DEPOSIT, AccountStatus.CLOSED, null, true));

        assertError(
                () -> service.requireWithdrawalDestination(ACCOUNT_ID, CUSTOMER_ID),
                AccountErrorCode.ACCOUNT_NOT_FOUND_OR_FORBIDDEN);
    }

    @Test
    void requireWithdrawalDestination_rejectsUnregisteredAccount() {
        stub(new MaturityAccountSnapshot(
                ACCOUNT_ID, null, AccountType.DEMAND_DEPOSIT, AccountStatus.ACTIVE, null, false));

        assertError(
                () -> service.requireWithdrawalDestination(ACCOUNT_ID, CUSTOMER_ID),
                AccountErrorCode.ACCOUNT_NOT_FOUND_OR_FORBIDDEN);
    }

    @Test
    void requireMatured_installmentSavings() {
        stub(new MaturityAccountSnapshot(
                ACCOUNT_ID, 10L, AccountType.INSTALLMENT_SAVINGS, AccountStatus.MATURED, MATURITY_DATE, false));

        var result = service.requireMatured(ACCOUNT_ID, CUSTOMER_ID, MATURITY_DATE);

        assertThat(result.timeDeposit()).isFalse();
    }

    private void stub(MaturityAccountSnapshot account) {
        when(accountPersistencePort.findMaturityAccountSnapshot(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(Optional.of(account));
    }

    private static void assertError(org.junit.jupiter.api.function.Executable action, AccountErrorCode expected) {
        assertThatThrownBy(action::execute)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }
}
