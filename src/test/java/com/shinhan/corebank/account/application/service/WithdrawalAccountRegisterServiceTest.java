package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.application.port.in.WithdrawalAccountRegisterCommand;
import com.shinhan.corebank.account.application.port.in.WithdrawalAccountRegisterResult;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.application.port.out.WithdrawalAccountAuthVerificationPort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.account.domain.exception.AccountErrorCode;
import com.shinhan.corebank.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WithdrawalAccountRegisterServiceTest {

    private static final Long CUSTOMER_ID = 1L;
    private static final Long ACCOUNT_ID = 10L;

    private static final String ACCOUNT_PASSWORD_AUTH_TOKEN =
            "APW-AUTH-TEST";

    private static final String OTP_AUTH_TOKEN =
            "OTP-AUTH-TEST";

    private static final String PASSWORD_HASH =
            "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq";

    private static final Instant FIXED_INSTANT =
            Instant.parse("2026-08-19T05:30:00Z");

    private static final OffsetDateTime EXPECTED_REGISTERED_AT =
            OffsetDateTime.parse(
                    "2026-08-19T14:30:00+09:00"
            );

    @Mock
    private AccountPersistencePort accountPersistencePort;

    @Mock
    private WithdrawalAccountAuthVerificationPort
            authVerificationPort;

    private WithdrawalAccountRegisterService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                FIXED_INSTANT,
                ZoneOffset.UTC
        );

        service = new WithdrawalAccountRegisterService(
                accountPersistencePort,
                authVerificationPort,
                clock
        );
    }

    @Test
    @DisplayName("본인 입출금계좌를 출금계좌로 등록한다")
    void registerWithdrawalAccount() {
        // given
        Account account = createAccount(
                AccountType.DEMAND_DEPOSIT,
                AccountStatus.ACTIVE,
                false,
                null
        );

        when(accountPersistencePort
                .findByAccountIdAndCustomerId(
                        ACCOUNT_ID,
                        CUSTOMER_ID
                ))
                .thenReturn(Optional.of(account));

        when(accountPersistencePort.save(any(Account.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        WithdrawalAccountRegisterCommand command =
                createCommand();

        // when
        WithdrawalAccountRegisterResult result =
                service.register(command);

        // then
        assertThat(result.accountId())
                .isEqualTo(ACCOUNT_ID);

        assertThat(result.registeredAt())
                .isEqualTo(EXPECTED_REGISTERED_AT);

        assertThat(account.isWithdrawalRegistered())
                .isTrue();

        assertThat(account.getWithdrawalRegisteredAt())
                .isEqualTo(
                        EXPECTED_REGISTERED_AT
                                .toLocalDateTime()
                );

        verify(accountPersistencePort)
                .save(account);
    }

    @Test
    @DisplayName("계좌비밀번호 인증 후 OTP 인증 순서로 검증한다")
    void verifyAuthenticationInOrder() {
        // given
        Account account = createAccount(
                AccountType.DEMAND_DEPOSIT,
                AccountStatus.ACTIVE,
                false,
                null
        );

        when(accountPersistencePort
                .findByAccountIdAndCustomerId(
                        ACCOUNT_ID,
                        CUSTOMER_ID
                ))
                .thenReturn(Optional.of(account));

        when(accountPersistencePort.save(any(Account.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        // when
        service.register(createCommand());

        // then
        InOrder inOrder =
                inOrder(authVerificationPort);

        inOrder.verify(authVerificationPort)
                .verifyAccountPasswordToken(
                        ACCOUNT_PASSWORD_AUTH_TOKEN,
                        CUSTOMER_ID,
                        ACCOUNT_ID
                );

        inOrder.verify(authVerificationPort)
                .verifyOtpToken(
                        OTP_AUTH_TOKEN,
                        CUSTOMER_ID,
                        ACCOUNT_ID
                );
    }

    @Test
    @DisplayName("이미 등록된 출금계좌도 인증한 뒤 기존 등록 시각을 반환하고 저장하지 않는다")
    void returnExistingRegistrationWhenAlreadyRegistered() {
        // given
        LocalDateTime existingRegisteredAt =
                LocalDateTime.of(
                        2026,
                        8,
                        10,
                        13,
                        20
                );

        Account account = createAccount(
                AccountType.DEMAND_DEPOSIT,
                AccountStatus.ACTIVE,
                true,
                existingRegisteredAt
        );

        when(accountPersistencePort
                .findByAccountIdAndCustomerId(
                        ACCOUNT_ID,
                        CUSTOMER_ID
                ))
                .thenReturn(Optional.of(account));

        // when
        WithdrawalAccountRegisterResult result =
                service.register(createCommand());

        // then
        assertThat(result.accountId())
                .isEqualTo(ACCOUNT_ID);

        assertThat(result.registeredAt())
                .isEqualTo(
                        existingRegisteredAt
                                .atOffset(
                                        ZoneOffset.ofHours(9)
                                )
                );

        verifyAuthenticationTokens();

        verify(accountPersistencePort, never())
                .save(any());
    }

    @Test
    @DisplayName("존재하지 않거나 접근할 수 없는 계좌는 ACC0201을 발생시킨다")
    void rejectNotFoundOrForbiddenAccount() {
        // given
        when(accountPersistencePort
                .findByAccountIdAndCustomerId(
                        ACCOUNT_ID,
                        CUSTOMER_ID
                ))
                .thenReturn(Optional.empty());

        // when
        BusinessException exception =
                catchThrowableOfType(
                        () -> service.register(
                                createCommand()
                        ),
                        BusinessException.class
                );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        AccountErrorCode
                                .ACCOUNT_NOT_FOUND_OR_FORBIDDEN
                );

        verifyNoInteractions(authVerificationPort);

        verify(accountPersistencePort, never())
                .save(any());
    }

    @Test
    @DisplayName("정기예금 계좌는 출금계좌로 등록할 수 없다")
    void rejectTimeDepositAccount() {
        // given
        Account account = createAccount(
                AccountType.TIME_DEPOSIT,
                AccountStatus.ACTIVE,
                false,
                null
        );

        when(accountPersistencePort
                .findByAccountIdAndCustomerId(
                        ACCOUNT_ID,
                        CUSTOMER_ID
                ))
                .thenReturn(Optional.of(account));

        // when
        BusinessException exception =
                catchThrowableOfType(
                        () -> service.register(
                                createCommand()
                        ),
                        BusinessException.class
                );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        AccountErrorCode
                                .INVALID_WITHDRAWAL_ACCOUNT_TYPE
                );

        verifyAuthenticationTokens();

        verify(accountPersistencePort, never())
                .save(any());
    }

    @Test
    @DisplayName("적금 계좌는 출금계좌로 등록할 수 없다")
    void rejectInstallmentSavingsAccount() {
        // given
        Account account = createAccount(
                AccountType.INSTALLMENT_SAVINGS,
                AccountStatus.ACTIVE,
                false,
                null
        );

        when(accountPersistencePort
                .findByAccountIdAndCustomerId(
                        ACCOUNT_ID,
                        CUSTOMER_ID
                ))
                .thenReturn(Optional.of(account));

        // when
        BusinessException exception =
                catchThrowableOfType(
                        () -> service.register(
                                createCommand()
                        ),
                        BusinessException.class
                );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        AccountErrorCode
                                .INVALID_WITHDRAWAL_ACCOUNT_TYPE
                );

        verifyAuthenticationTokens();

        verify(accountPersistencePort, never())
                .save(any());
    }

    @Test
    @DisplayName("거래정지 계좌는 출금계좌로 등록할 수 없다")
    void rejectSuspendedAccount() {
        // given
        Account account = createAccount(
                AccountType.DEMAND_DEPOSIT,
                AccountStatus.SUSPENDED,
                false,
                null
        );

        when(accountPersistencePort
                .findByAccountIdAndCustomerId(
                        ACCOUNT_ID,
                        CUSTOMER_ID
                ))
                .thenReturn(Optional.of(account));

        // when
        BusinessException exception =
                catchThrowableOfType(
                        () -> service.register(
                                createCommand()
                        ),
                        BusinessException.class
                );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        AccountErrorCode
                                .INVALID_ACCOUNT_STATUS
                );

        verifyAuthenticationTokens();

        verify(accountPersistencePort, never())
                .save(any());
    }

    @Test
    @DisplayName("해지 계좌는 출금계좌로 등록할 수 없다")
    void rejectClosedAccount() {
        // given
        Account account = createAccount(
                AccountType.DEMAND_DEPOSIT,
                AccountStatus.CLOSED,
                false,
                null
        );

        when(accountPersistencePort
                .findByAccountIdAndCustomerId(
                        ACCOUNT_ID,
                        CUSTOMER_ID
                ))
                .thenReturn(Optional.of(account));

        // when
        BusinessException exception =
                catchThrowableOfType(
                        () -> service.register(
                                createCommand()
                        ),
                        BusinessException.class
                );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        AccountErrorCode
                                .INVALID_ACCOUNT_STATUS
                );

        verifyAuthenticationTokens();

        verify(accountPersistencePort, never())
                .save(any());
    }

    @Test
    @DisplayName("계좌비밀번호 인증토큰 검증에 실패하면 OTP를 검증하거나 계좌를 저장하지 않는다")
    void doNotSaveWhenAccountPasswordTokenVerificationFails() {
        // given
        Account account = createAccount(
                AccountType.DEMAND_DEPOSIT,
                AccountStatus.ACTIVE,
                false,
                null
        );

        when(accountPersistencePort
                .findByAccountIdAndCustomerId(
                        ACCOUNT_ID,
                        CUSTOMER_ID
                ))
                .thenReturn(Optional.of(account));

        doThrow(
                new RuntimeException(
                        "계좌비밀번호 인증 실패"
                )
        )
                .when(authVerificationPort)
                .verifyAccountPasswordToken(
                        ACCOUNT_PASSWORD_AUTH_TOKEN,
                        CUSTOMER_ID,
                        ACCOUNT_ID
                );

        // when & then
        assertThatThrownBy(
                () -> service.register(
                        createCommand()
                )
        )
                .isInstanceOf(RuntimeException.class)
                .hasMessage(
                        "계좌비밀번호 인증 실패"
                );

        verify(authVerificationPort, never())
                .verifyOtpToken(
                        any(),
                        any(),
                        any()
                );

        verify(accountPersistencePort, never())
                .save(any());

        assertThat(account.isWithdrawalRegistered())
                .isFalse();

        assertThat(account.getWithdrawalRegisteredAt())
                .isNull();
    }

    @Test
    @DisplayName("OTP 인증토큰 검증에 실패하면 계좌를 저장하거나 등록 상태로 변경하지 않는다")
    void doNotSaveWhenOtpTokenVerificationFails() {
        // given
        Account account = createAccount(
                AccountType.DEMAND_DEPOSIT,
                AccountStatus.ACTIVE,
                false,
                null
        );

        when(accountPersistencePort
                .findByAccountIdAndCustomerId(
                        ACCOUNT_ID,
                        CUSTOMER_ID
                ))
                .thenReturn(Optional.of(account));

        doThrow(
                new RuntimeException(
                        "OTP 인증 실패"
                )
        )
                .when(authVerificationPort)
                .verifyOtpToken(
                        OTP_AUTH_TOKEN,
                        CUSTOMER_ID,
                        ACCOUNT_ID
                );

        // when & then
        assertThatThrownBy(
                () -> service.register(
                        createCommand()
                )
        )
                .isInstanceOf(RuntimeException.class)
                .hasMessage(
                        "OTP 인증 실패"
                );

        verify(authVerificationPort)
                .verifyAccountPasswordToken(
                        ACCOUNT_PASSWORD_AUTH_TOKEN,
                        CUSTOMER_ID,
                        ACCOUNT_ID
                );

        verify(accountPersistencePort, never())
                .save(any());

        assertThat(account.isWithdrawalRegistered())
                .isFalse();

        assertThat(account.getWithdrawalRegisteredAt())
                .isNull();
    }

    private void verifyAuthenticationTokens() {
        verify(authVerificationPort)
                .verifyAccountPasswordToken(
                        ACCOUNT_PASSWORD_AUTH_TOKEN,
                        CUSTOMER_ID,
                        ACCOUNT_ID
                );

        verify(authVerificationPort)
                .verifyOtpToken(
                        OTP_AUTH_TOKEN,
                        CUSTOMER_ID,
                        ACCOUNT_ID
                );
    }

    private WithdrawalAccountRegisterCommand createCommand() {
        return new WithdrawalAccountRegisterCommand(
                CUSTOMER_ID,
                ACCOUNT_ID,
                ACCOUNT_PASSWORD_AUTH_TOKEN,
                OTP_AUTH_TOKEN
        );
    }

    private Account createAccount(
            AccountType accountType,
            AccountStatus status,
            boolean withdrawalRegistered,
            LocalDateTime withdrawalRegisteredAt
    ) {
        Long productId =
                accountType == AccountType.DEMAND_DEPOSIT
                        ? null
                        : 100L;

        LocalDate maturityDate =
                accountType == AccountType.DEMAND_DEPOSIT
                        ? null
                        : LocalDate.of(
                        2027,
                        8,
                        1
                );

        LocalDateTime closedDate =
                status == AccountStatus.CLOSED
                        ? LocalDateTime.of(
                        2026,
                        8,
                        15,
                        10,
                        0
                )
                        : null;

        return Account.reconstitute(
                ACCOUNT_ID,
                "088100000001",
                CUSTOMER_ID,
                productId,
                accountType,
                1_000_000L,
                status,
                PASSWORD_HASH,
                0,
                false,
                null,
                null,
                withdrawalRegistered,
                withdrawalRegisteredAt,
                LocalDateTime.of(
                        2026,
                        8,
                        1,
                        10,
                        0
                ),
                maturityDate,
                closedDate,
                LocalDateTime.of(
                        2026,
                        8,
                        10,
                        15,
                        0
                ),
                0L,
                LocalDateTime.of(
                        2026,
                        8,
                        1,
                        10,
                        0
                ),
                LocalDateTime.of(
                        2026,
                        8,
                        10,
                        15,
                        0
                )
        );
    }
}
