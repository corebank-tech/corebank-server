package com.shinhan.corebank.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.shinhan.corebank.account.application.port.in.ChangeAccountPasswordCommand;
import com.shinhan.corebank.account.application.port.in.ChangeAccountPasswordResult;
import com.shinhan.corebank.account.application.port.out.AccountPasswordChangeAuthVerificationPort;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.account.domain.exception.AccountErrorCode;
import com.shinhan.corebank.account.domain.exception.AccountPasswordErrorCode;
import com.shinhan.corebank.common.audit.AuditEventType;
import com.shinhan.corebank.common.audit.AuditLogService;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

// 계좌비밀번호 변경의 입력·계좌 상태·인증 순서·저장 결과를 검증한다.
@ExtendWith(MockitoExtension.class)
class AccountPasswordChangeServiceTest {

    private static final Long CUSTOMER_ID = 1L;
    private static final Long ACCOUNT_ID = 101L;
    private static final String OLD_HASH = "old-password-hash";
    private static final String NEW_HASH = "new-password-hash";

    @Mock
    private AccountPersistencePort accountPersistencePort;

    @Mock
    private AccountPasswordChangeAuthVerificationPort authVerificationPort;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditLogService auditLogService;

    private AccountPasswordChangeService service;

    @BeforeEach
    void setUp() {
        service = new AccountPasswordChangeService(
                accountPersistencePort, authVerificationPort, passwordEncoder, auditLogService);
    }

    @Test
    @DisplayName("예금 계좌도 두 인증을 소비하고 BCrypt 해시와 오류 상태를 변경한다")
    void changesProductAccountPassword() {
        Account initialAccount = account(
                AccountType.TIME_DEPOSIT,
                AccountStatus.ACTIVE,
                3,
                false,
                OLD_HASH,
                LocalDateTime.of(2026, 8, 20, 10, 0));
        Account lockedAccount = account(
                AccountType.TIME_DEPOSIT,
                AccountStatus.ACTIVE,
                3,
                false,
                OLD_HASH,
                LocalDateTime.of(2026, 8, 20, 10, 0));
        Account saved = account(
                AccountType.TIME_DEPOSIT,
                AccountStatus.ACTIVE,
                0,
                false,
                NEW_HASH,
                LocalDateTime.of(2026, 8, 22, 13, 30));
        when(accountPersistencePort.findByAccountIdAndCustomerId(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(Optional.of(initialAccount));
        when(accountPersistencePort.findByAccountIdAndCustomerIdForUpdate(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(Optional.of(lockedAccount));
        when(passwordEncoder.encode("5678")).thenReturn(NEW_HASH);
        when(accountPersistencePort.updatePasswordState(lockedAccount)).thenReturn(saved);

        ChangeAccountPasswordResult result = service.change(command("5678", "5678"));

        assertThat(initialAccount.getPasswordHash()).isEqualTo(OLD_HASH);
        assertThat(lockedAccount.getPasswordHash()).isEqualTo(NEW_HASH);
        assertThat(lockedAccount.getPasswordFailureCount()).isZero();
        assertThat(lockedAccount.isPasswordLocked()).isFalse();
        assertThat(result.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(result.updatedAt()).isEqualTo(OffsetDateTime.parse("2026-08-22T13:30:00+09:00"));

        InOrder inOrder = inOrder(accountPersistencePort, authVerificationPort, passwordEncoder);
        inOrder.verify(accountPersistencePort).findByAccountIdAndCustomerId(ACCOUNT_ID, CUSTOMER_ID);
        inOrder.verify(authVerificationPort).verifyAccountPasswordToken("password-token", CUSTOMER_ID, ACCOUNT_ID);
        inOrder.verify(authVerificationPort).verifyOtpToken("otp-token", CUSTOMER_ID, ACCOUNT_ID);
        inOrder.verify(passwordEncoder).encode("5678");
        inOrder.verify(accountPersistencePort).findByAccountIdAndCustomerIdForUpdate(ACCOUNT_ID, CUSTOMER_ID);
        inOrder.verify(accountPersistencePort).updatePasswordState(lockedAccount);
        verify(auditLogService)
                .record(
                        CUSTOMER_ID,
                        null,
                        AuditEventType.ACCOUNT_PASSWORD_CHANGE,
                        "127.0.0.1",
                        true,
                        java.util.Map.of("accountId", ACCOUNT_ID));
    }

    @Test
    @DisplayName("신규 비밀번호 확인값이 다르면 APW0002이고 인증 토큰을 소비하지 않는다")
    void rejectsPasswordConfirmationMismatch() {
        BusinessException exception =
                catchThrowableOfType(() -> service.change(command("5678", "1234")), BusinessException.class);

        assertThat(exception.getErrorCode()).isEqualTo(AccountPasswordErrorCode.NEW_PASSWORD_CONFIRM_MISMATCH);
        verifyNoInteractions(accountPersistencePort, authVerificationPort, passwordEncoder);
    }

    @Test
    @DisplayName("신규 비밀번호가 숫자 4자리가 아니면 CMN0001이다")
    void rejectsInvalidPasswordFormat() {
        BusinessException exception =
                catchThrowableOfType(() -> service.change(command("12ab", "12ab")), BusinessException.class);

        assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT);
        verifyNoInteractions(authVerificationPort);
    }

    @Test
    @DisplayName("타인 소유 또는 없는 계좌는 ACC0201이고 인증 토큰을 소비하지 않는다")
    void rejectsNotFoundOrForbiddenAccount() {
        when(accountPersistencePort.findByAccountIdAndCustomerId(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(Optional.empty());

        BusinessException exception =
                catchThrowableOfType(() -> service.change(command("5678", "5678")), BusinessException.class);

        assertThat(exception.getErrorCode()).isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND_OR_FORBIDDEN);
        verifyNoInteractions(authVerificationPort, passwordEncoder);
    }

    @Test
    @DisplayName("거래정지 계좌는 ACC0301이고 인증 토큰을 소비하지 않는다")
    void rejectsSuspendedAccount() {
        Account account = account(
                AccountType.DEMAND_DEPOSIT,
                AccountStatus.SUSPENDED,
                0,
                false,
                OLD_HASH,
                LocalDateTime.of(2026, 8, 20, 10, 0));
        when(accountPersistencePort.findByAccountIdAndCustomerId(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(Optional.of(account));

        BusinessException exception =
                catchThrowableOfType(() -> service.change(command("5678", "5678")), BusinessException.class);

        assertThat(exception.getErrorCode()).isEqualTo(AccountErrorCode.INVALID_ACCOUNT_STATUS);
        verifyNoInteractions(authVerificationPort, passwordEncoder);
        verify(accountPersistencePort, never()).updatePasswordState(any());
    }

    @Test
    @DisplayName("OTP 인증 실패 시 신규 비밀번호를 암호화하거나 저장하지 않는다")
    void doesNotChangePasswordWhenOtpVerificationFails() {
        Account account = account(
                AccountType.INSTALLMENT_SAVINGS,
                AccountStatus.ACTIVE,
                0,
                false,
                OLD_HASH,
                LocalDateTime.of(2026, 8, 20, 10, 0));
        when(accountPersistencePort.findByAccountIdAndCustomerId(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(Optional.of(account));
        doThrow(new RuntimeException("OTP 인증 실패"))
                .when(authVerificationPort)
                .verifyOtpToken("otp-token", CUSTOMER_ID, ACCOUNT_ID);

        RuntimeException exception =
                catchThrowableOfType(() -> service.change(command("5678", "5678")), RuntimeException.class);

        assertThat(exception).hasMessage("OTP 인증 실패");
        assertThat(account.getPasswordHash()).isEqualTo(OLD_HASH);
        verifyNoInteractions(passwordEncoder, auditLogService);
        verify(accountPersistencePort, never()).findByAccountIdAndCustomerIdForUpdate(any(), any());
        verify(accountPersistencePort, never()).updatePasswordState(any());
    }

    @Test
    @DisplayName("토큰 검증 후 락 재조회에서 계좌가 없으면 ACC0201이고 저장하지 않는다")
    void rejectsAccountMissingWhenLockIsAcquired() {
        Account initialAccount = account(
                AccountType.DEMAND_DEPOSIT,
                AccountStatus.ACTIVE,
                0,
                false,
                OLD_HASH,
                LocalDateTime.of(2026, 8, 20, 10, 0));
        when(accountPersistencePort.findByAccountIdAndCustomerId(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(Optional.of(initialAccount));
        when(accountPersistencePort.findByAccountIdAndCustomerIdForUpdate(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(Optional.empty());

        BusinessException exception =
                catchThrowableOfType(() -> service.change(command("5678", "5678")), BusinessException.class);

        assertThat(exception.getErrorCode()).isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND_OR_FORBIDDEN);
        verify(authVerificationPort).verifyAccountPasswordToken("password-token", CUSTOMER_ID, ACCOUNT_ID);
        verify(authVerificationPort).verifyOtpToken("otp-token", CUSTOMER_ID, ACCOUNT_ID);
        verify(passwordEncoder).encode("5678");
        verifyNoInteractions(auditLogService);
        verify(accountPersistencePort, never()).updatePasswordState(any());
    }

    @Test
    @DisplayName("토큰 검증 중 계좌가 거래정지되면 락 재조회 상태 검증에서 차단한다")
    void rejectsAccountSuspendedBeforeLockIsAcquired() {
        Account initialAccount = account(
                AccountType.DEMAND_DEPOSIT,
                AccountStatus.ACTIVE,
                0,
                false,
                OLD_HASH,
                LocalDateTime.of(2026, 8, 20, 10, 0));
        Account suspendedAccount = account(
                AccountType.DEMAND_DEPOSIT,
                AccountStatus.SUSPENDED,
                0,
                false,
                OLD_HASH,
                LocalDateTime.of(2026, 8, 22, 14, 0));
        when(accountPersistencePort.findByAccountIdAndCustomerId(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(Optional.of(initialAccount));
        when(accountPersistencePort.findByAccountIdAndCustomerIdForUpdate(ACCOUNT_ID, CUSTOMER_ID))
                .thenReturn(Optional.of(suspendedAccount));

        BusinessException exception =
                catchThrowableOfType(() -> service.change(command("5678", "5678")), BusinessException.class);

        assertThat(exception.getErrorCode()).isEqualTo(AccountErrorCode.INVALID_ACCOUNT_STATUS);
        verify(passwordEncoder).encode("5678");
        verifyNoInteractions(auditLogService);
        verify(accountPersistencePort, never()).updatePasswordState(any());
    }

    private ChangeAccountPasswordCommand command(String password, String confirmation) {
        return new ChangeAccountPasswordCommand(
                CUSTOMER_ID, ACCOUNT_ID, "otp-token", "password-token", password, confirmation, "127.0.0.1");
    }

    private Account account(
            AccountType type,
            AccountStatus status,
            int failureCount,
            boolean locked,
            String passwordHash,
            LocalDateTime updatedAt) {
        Long productId = type == AccountType.DEMAND_DEPOSIT ? null : 10L;
        LocalDate maturityDate = type == AccountType.DEMAND_DEPOSIT ? null : LocalDate.of(2027, 8, 22);

        return Account.reconstitute(
                ACCOUNT_ID,
                "110123456789",
                CUSTOMER_ID,
                productId,
                type,
                1_000_000L,
                status,
                passwordHash,
                failureCount,
                locked,
                null,
                null,
                false,
                null,
                LocalDateTime.of(2025, 8, 22, 10, 0),
                maturityDate,
                null,
                null,
                0L,
                LocalDateTime.of(2025, 8, 22, 10, 0),
                updatedAt);
    }
}
