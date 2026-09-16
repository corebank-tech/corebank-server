package com.shinhan.corebank.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.account.application.port.in.AccountOpeningResult;
import com.shinhan.corebank.account.application.port.in.IssueAccountNumberUseCase;
import com.shinhan.corebank.account.application.port.in.ProductAccountOpeningCommand;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductAccountOpeningServiceTest {

    private static final Long CUSTOMER_ID = 1L;
    private static final Long PRODUCT_ID = 10L;
    private static final Long ACCOUNT_ID = 100L;

    private static final String ACCOUNT_NUMBER = "088200000001";

    private static final String PASSWORD_HASH = "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq";

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private static final LocalDateTime OPENED_DATE = LocalDateTime.of(2026, 8, 11, 23, 0);

    private static final LocalDate MATURITY_DATE = LocalDate.of(2027, 8, 11);

    @Mock
    private IssueAccountNumberUseCase issueAccountNumberUseCase;

    @Mock
    private AccountPersistencePort accountPersistencePort;

    private ProductAccountOpeningService productAccountOpeningService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-11T14:00:00Z"), KST);

        productAccountOpeningService =
                new ProductAccountOpeningService(issueAccountNumberUseCase, accountPersistencePort, clock);
    }

    @Test
    @DisplayName("정기예금 계좌를 개설하면 계좌번호를 발급하고 계좌를 저장한다")
    void openTimeDepositAccount() {
        // given
        ProductAccountOpeningCommand command = new ProductAccountOpeningCommand(
                CUSTOMER_ID, PRODUCT_ID, AccountType.TIME_DEPOSIT, PASSWORD_HASH, MATURITY_DATE);

        when(issueAccountNumberUseCase.issue(AccountType.TIME_DEPOSIT, PRODUCT_ID))
                .thenReturn(ACCOUNT_NUMBER);

        when(accountPersistencePort.save(any(Account.class)))
                .thenAnswer(invocation -> savedAccount(invocation.getArgument(0)));

        // when
        AccountOpeningResult result = productAccountOpeningService.open(command);

        // then
        assertThat(result.accountId()).isEqualTo(ACCOUNT_ID);

        assertThat(result.accountNumber()).isEqualTo(ACCOUNT_NUMBER);

        verify(issueAccountNumberUseCase).issue(AccountType.TIME_DEPOSIT, PRODUCT_ID);

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);

        verify(accountPersistencePort).save(accountCaptor.capture());

        Account account = accountCaptor.getValue();

        assertThat(account.getAccountNumber()).isEqualTo(ACCOUNT_NUMBER);

        assertThat(account.getCustomerId()).isEqualTo(CUSTOMER_ID);

        assertThat(account.getProductId()).isEqualTo(PRODUCT_ID);

        assertThat(account.getAccountType()).isEqualTo(AccountType.TIME_DEPOSIT);

        assertThat(account.getPasswordHash()).isEqualTo(PASSWORD_HASH);

        assertThat(account.getOpenedDate()).isEqualTo(OPENED_DATE);

        assertThat(account.getMaturityDate()).isEqualTo(MATURITY_DATE);

        assertThat(account.getBalance()).isZero();

        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);

        assertThat(account.getPasswordFailureCount()).isZero();

        assertThat(account.isPasswordLocked()).isFalse();

        assertThat(account.isWithdrawalRegistered()).isFalse();
    }

    @Test
    @DisplayName("정기적금 계좌도 전달받은 상품과 만기일로 개설한다")
    void openInstallmentSavingsAccount() {
        // given
        ProductAccountOpeningCommand command = new ProductAccountOpeningCommand(
                CUSTOMER_ID, PRODUCT_ID, AccountType.INSTALLMENT_SAVINGS, PASSWORD_HASH, MATURITY_DATE);

        when(issueAccountNumberUseCase.issue(AccountType.INSTALLMENT_SAVINGS, PRODUCT_ID))
                .thenReturn(ACCOUNT_NUMBER);

        when(accountPersistencePort.save(any(Account.class)))
                .thenAnswer(invocation -> savedAccount(invocation.getArgument(0)));

        // when
        AccountOpeningResult result = productAccountOpeningService.open(command);

        // then
        assertThat(result.accountId()).isEqualTo(ACCOUNT_ID);

        assertThat(result.accountNumber()).isEqualTo(ACCOUNT_NUMBER);

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);

        verify(accountPersistencePort).save(accountCaptor.capture());

        Account account = accountCaptor.getValue();

        assertThat(account.getAccountType()).isEqualTo(AccountType.INSTALLMENT_SAVINGS);

        assertThat(account.getProductId()).isEqualTo(PRODUCT_ID);

        assertThat(account.getMaturityDate()).isEqualTo(MATURITY_DATE);
    }

    private Account savedAccount(Account account) {
        return Account.reconstitute(
                ACCOUNT_ID,
                account.getAccountNumber(),
                account.getCustomerId(),
                account.getProductId(),
                account.getAccountType(),
                account.getBalance(),
                account.getStatus(),
                account.getPasswordHash(),
                account.getPasswordFailureCount(),
                account.isPasswordLocked(),
                account.getAlias(),
                account.getDisplayOrder(),
                account.isWithdrawalRegistered(),
                account.getWithdrawalRegisteredAt(),
                account.getOpenedDate(),
                account.getMaturityDate(),
                account.getClosedDate(),
                account.getLastTransactionAt(),
                0L,
                OPENED_DATE,
                OPENED_DATE);
    }

    @Test
    @DisplayName("상품 계좌 개설에서는 입출금계좌를 생성할 수 없다")
    void rejectDemandDepositAccount() {
        // given
        ProductAccountOpeningCommand command = new ProductAccountOpeningCommand(
                CUSTOMER_ID, PRODUCT_ID, AccountType.DEMAND_DEPOSIT, PASSWORD_HASH, MATURITY_DATE);

        // when & then
        assertThatThrownBy(() -> productAccountOpeningService.open(command))
                .isInstanceOf(BusinessException.class)
                .satisfies(throwable -> {
                    BusinessException exception = (BusinessException) throwable;

                    assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT);
                });

        verifyNoInteractions(issueAccountNumberUseCase);
        verifyNoInteractions(accountPersistencePort);
    }

    @Test
    @DisplayName("상품 계좌 개설 command가 없으면 채번하지 않는다")
    void rejectNullCommand() {
        assertThatThrownBy(() -> productAccountOpeningService.open(null))
                .isInstanceOf(BusinessException.class)
                .satisfies(throwable -> {
                    BusinessException exception = (BusinessException) throwable;

                    assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING);
                });

        verifyNoInteractions(issueAccountNumberUseCase);
        verifyNoInteractions(accountPersistencePort);
    }
}
