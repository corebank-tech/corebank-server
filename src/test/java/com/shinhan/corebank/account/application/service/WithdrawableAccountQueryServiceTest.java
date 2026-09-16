package com.shinhan.corebank.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.account.application.port.in.WithdrawableAccountResult;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.AccountType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WithdrawableAccountQueryServiceTest {

    private static final Long CUSTOMER_ID = 1L;
    private static final Long ACCOUNT_ID = 10L;
    private static final String ACCOUNT_NUMBER = "088100000001";
    private static final long BALANCE = 1_000_000L;

    private static final String PASSWORD_HASH = "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq";

    private static final LocalDateTime OPENED_DATE = LocalDateTime.of(2026, 8, 1, 10, 0);
    private static final LocalDateTime REGISTERED_AT = LocalDateTime.of(2026, 8, 10, 15, 0);

    @Mock
    private AccountPersistencePort accountPersistencePort;

    private WithdrawableAccountQueryService service;

    @BeforeEach
    void setUp() {
        service = new WithdrawableAccountQueryService(accountPersistencePort);
    }

    @Test
    @DisplayName("본인 소유 활성 출금등록 계좌면 계좌ID·계좌번호·잔액을 반환한다")
    void findWithdrawable() {
        // given
        givenAccount(Optional.of(createAccount(AccountStatus.ACTIVE, true)));

        // when
        Optional<WithdrawableAccountResult> result = service.findWithdrawable(ACCOUNT_ID, CUSTOMER_ID);

        // then
        assertThat(result).contains(new WithdrawableAccountResult(ACCOUNT_ID, ACCOUNT_NUMBER, BALANCE));
    }

    @Test
    @DisplayName("본인 계좌가 아니거나 없으면 빈 값을 반환한다")
    void findWithdrawableNotOwned() {
        // given
        givenAccount(Optional.empty());

        // when
        Optional<WithdrawableAccountResult> result = service.findWithdrawable(ACCOUNT_ID, CUSTOMER_ID);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("활성 상태가 아니면 빈 값을 반환한다")
    void findWithdrawableNotActive() {
        // given
        givenAccount(Optional.of(createAccount(AccountStatus.SUSPENDED, true)));

        // when
        Optional<WithdrawableAccountResult> result = service.findWithdrawable(ACCOUNT_ID, CUSTOMER_ID);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("출금계좌로 등록되지 않았으면 빈 값을 반환한다")
    void findWithdrawableNotRegistered() {
        // given
        givenAccount(Optional.of(createAccount(AccountStatus.ACTIVE, false)));

        // when
        Optional<WithdrawableAccountResult> result = service.findWithdrawable(ACCOUNT_ID, CUSTOMER_ID);

        // then
        assertThat(result).isEmpty();
    }

    private void givenAccount(Optional<Account> account) {
        when(accountPersistencePort.findByAccountIdAndCustomerId(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(account);
    }

    private Account createAccount(AccountStatus status, boolean withdrawalRegistered) {
        return Account.reconstitute(
                ACCOUNT_ID,
                ACCOUNT_NUMBER,
                CUSTOMER_ID,
                null,
                AccountType.DEMAND_DEPOSIT,
                BALANCE,
                status,
                PASSWORD_HASH,
                0,
                false,
                null,
                null,
                withdrawalRegistered,
                withdrawalRegistered ? REGISTERED_AT : null,
                OPENED_DATE,
                null,
                null,
                REGISTERED_AT,
                0L,
                OPENED_DATE,
                REGISTERED_AT);
    }
}
