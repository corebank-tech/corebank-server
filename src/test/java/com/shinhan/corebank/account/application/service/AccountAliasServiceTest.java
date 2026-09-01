package com.shinhan.corebank.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.shinhan.corebank.account.application.port.in.AccountAliasResult;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.account.domain.exception.AccountErrorCode;
import com.shinhan.corebank.common.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountAliasServiceTest {

    private static final Long CUSTOMER_ID = 1L;
    private static final Long ACCOUNT_ID = 10L;

    private static final String PASSWORD_HASH = "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq";

    @Mock
    private AccountPersistencePort accountPersistencePort;

    private AccountAliasService service;

    @BeforeEach
    void setUp() {
        service = new AccountAliasService(accountPersistencePort);
    }

    @Test
    @DisplayName("본인 계좌에 별명을 등록한다")
    void changeAlias() {
        // given
        Account account = createAccount(null);

        when(accountPersistencePort.findByAccountIdAndCustomerId(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(Optional.of(account));

        when(accountPersistencePort.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        AccountAliasResult result = service.changeAlias(CUSTOMER_ID, ACCOUNT_ID, "생활비통장");

        // then
        assertThat(result.accountId()).isEqualTo(ACCOUNT_ID);

        assertThat(result.alias()).isEqualTo("생활비통장");

        assertThat(account.getAlias()).isEqualTo("생활비통장");

        verify(accountPersistencePort).save(account);
    }

    @Test
    @DisplayName("기존 계좌별명을 수정한다")
    void updateExistingAlias() {
        // given
        Account account = createAccount("생활비통장");

        when(accountPersistencePort.findByAccountIdAndCustomerId(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(Optional.of(account));

        when(accountPersistencePort.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        AccountAliasResult result = service.changeAlias(CUSTOMER_ID, ACCOUNT_ID, "급여통장");

        // then
        assertThat(result.alias()).isEqualTo("급여통장");

        assertThat(account.getAlias()).isEqualTo("급여통장");
    }

    @Test
    @DisplayName("계좌별명을 삭제한다")
    void deleteAlias() {
        // given
        Account account = createAccount("생활비통장");

        when(accountPersistencePort.findByAccountIdAndCustomerId(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(Optional.of(account));

        when(accountPersistencePort.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        service.deleteAlias(CUSTOMER_ID, ACCOUNT_ID);

        // then
        assertThat(account.getAlias()).isNull();

        verify(accountPersistencePort).save(account);
    }

    @Test
    @DisplayName("존재하지 않거나 접근할 수 없는 계좌는 ACC0201을 발생시킨다")
    void rejectNotFoundOrForbiddenAccount() {
        // given
        when(accountPersistencePort.findByAccountIdAndCustomerId(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(Optional.empty());

        // when
        BusinessException exception = catchThrowableOfType(
                () -> service.changeAlias(CUSTOMER_ID, ACCOUNT_ID, "생활비통장"), BusinessException.class);

        // then
        assertThat(exception.getErrorCode()).isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND_OR_FORBIDDEN);

        verify(accountPersistencePort, never()).save(any());
    }

    @Test
    @DisplayName("별명을 변경해도 계좌의 다른 상태값은 변경하지 않는다")
    void changingAliasDoesNotChangeOtherAccountState() {
        // given
        Account account = createAccount(null);

        long balanceBefore = account.getBalance();
        AccountStatus statusBefore = account.getStatus();

        when(accountPersistencePort.findByAccountIdAndCustomerId(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(Optional.of(account));

        when(accountPersistencePort.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        service.changeAlias(CUSTOMER_ID, ACCOUNT_ID, "생활비통장");

        // then
        assertThat(account.getBalance()).isEqualTo(balanceBefore);

        assertThat(account.getStatus()).isEqualTo(statusBefore);
    }

    private Account createAccount(String alias) {
        return Account.reconstitute(
                ACCOUNT_ID,
                "088100000001",
                CUSTOMER_ID,
                null,
                AccountType.DEMAND_DEPOSIT,
                1_000_000L,
                AccountStatus.ACTIVE,
                PASSWORD_HASH,
                0,
                false,
                alias,
                null,
                false,
                null,
                LocalDateTime.of(2026, 8, 1, 10, 0),
                null,
                null,
                LocalDateTime.of(2026, 8, 10, 15, 0),
                0L,
                LocalDateTime.of(2026, 8, 1, 10, 0),
                LocalDateTime.of(2026, 8, 10, 15, 0));
    }
}
