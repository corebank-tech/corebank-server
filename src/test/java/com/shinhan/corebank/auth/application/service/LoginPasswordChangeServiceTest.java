package com.shinhan.corebank.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.shinhan.corebank.auth.application.port.in.ChangeLoginPasswordCommand;
import com.shinhan.corebank.auth.domain.exception.AuthErrorCode;
import com.shinhan.corebank.common.audit.AuditEventType;
import com.shinhan.corebank.common.audit.AuditLogService;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.customer.api.CustomerAuthenticationFacade;
import com.shinhan.corebank.customer.api.PasswordResetCustomerData;
import com.shinhan.corebank.customer.api.ResetCustomerPasswordCommand;
import com.shinhan.corebank.customer.api.ResetCustomerPasswordResult;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

// 로그인 비밀번호 변경의 검증 순서와 저장·감사 결과를 검증한다.
@ExtendWith(MockitoExtension.class)
class LoginPasswordChangeServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-20T09:30:00Z"), ZoneId.of("Asia/Seoul"));
    private static final PasswordResetCustomerData CUSTOMER =
            new PasswordResetCustomerData(1L, "user01", "홍길동", "user@example.com", "old-hash", false);

    @Mock
    CustomerAuthenticationFacade customerFacade;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    AuditLogService auditLogService;

    private LoginPasswordChangeService service;

    @BeforeEach
    void setUp() {
        service = new LoginPasswordChangeService(customerFacade, passwordEncoder, auditLogService, CLOCK);
    }

    @Test
    @DisplayName("현재 비밀번호를 확인하고 신규 해시 저장과 성공 감사를 기록한다")
    void changesPassword() {
        given(customerFacade.findPasswordResetCustomerById(1L)).willReturn(CUSTOMER);
        given(passwordEncoder.matches("Current1!", "old-hash")).willReturn(true);
        given(passwordEncoder.matches("NewPass8!x", "old-hash")).willReturn(false);
        given(passwordEncoder.encode("NewPass8!x")).willReturn("new-hash");
        given(customerFacade.resetPassword(any())).willReturn(ResetCustomerPasswordResult.COMPLETED);

        var result = service.change(command("Current1!", "NewPass8!x", "NewPass8!x"));

        assertThat(result.customerId()).isEqualTo(1L);
        assertThat(result.passwordChangedAt()).isEqualTo(OffsetDateTime.parse("2026-09-20T18:30+09:00"));
        verify(customerFacade)
                .resetPassword(new ResetCustomerPasswordCommand(
                        1L, "old-hash", "new-hash", LocalDateTime.of(2026, 9, 20, 18, 30)));
        verify(auditLogService).record(1L, null, AuditEventType.LOGIN_PASSWORD_CHANGE, "127.0.0.1", true, Map.of());
    }

    @Test
    @DisplayName("신규 비밀번호 확인값이 다르면 ATH0002를 반환한다")
    void rejectsConfirmationMismatch() {
        assertError(command("Current1!", "NewPass8!x", "Different1!"), AuthErrorCode.PASSWORD_CONFIRMATION_MISMATCH);
    }

    @Test
    @DisplayName("잠긴 고객은 ATH0102를 반환한다")
    void rejectsLockedCustomer() {
        given(customerFacade.findPasswordResetCustomerById(1L))
                .willReturn(new PasswordResetCustomerData(1L, "user01", "홍길동", "a@b.com", "old-hash", true));
        assertError(command("Current1!", "NewPass8!x", "NewPass8!x"), AuthErrorCode.ACCOUNT_LOCKED);
    }

    @Test
    @DisplayName("현재 비밀번호가 다르면 ATH0010을 반환한다")
    void rejectsCurrentPasswordMismatch() {
        given(customerFacade.findPasswordResetCustomerById(1L)).willReturn(CUSTOMER);
        given(passwordEncoder.matches("Wrong1!", "old-hash")).willReturn(false);
        assertError(command("Wrong1!", "NewPass8!x", "NewPass8!x"), AuthErrorCode.CURRENT_PASSWORD_MISMATCH);
    }

    @Test
    @DisplayName("금칙 조건을 위반한 신규 비밀번호는 ATH0001을 반환한다")
    void rejectsForbiddenPassword() {
        given(customerFacade.findPasswordResetCustomerById(1L)).willReturn(CUSTOMER);
        given(passwordEncoder.matches("Current1!", "old-hash")).willReturn(true);
        assertError(command("Current1!", "Ab!1234x", "Ab!1234x"), AuthErrorCode.INVALID_PASSWORD_FORMAT);
    }

    @Test
    @DisplayName("직전 비밀번호를 재사용하면 ATH0003을 반환한다")
    void rejectsPreviousPasswordReuse() {
        given(customerFacade.findPasswordResetCustomerById(1L)).willReturn(CUSTOMER);
        given(passwordEncoder.matches("Current1!", "old-hash")).willReturn(true);
        assertError(command("Current1!", "Current1!", "Current1!"), AuthErrorCode.PREVIOUS_PASSWORD_REUSE);
    }

    private void assertError(ChangeLoginPasswordCommand command, AuthErrorCode expected) {
        BusinessException exception = catchThrowableOfType(() -> service.change(command), BusinessException.class);
        assertThat(exception.getErrorCode()).isEqualTo(expected);
        verify(customerFacade, never()).resetPassword(any());
        verify(auditLogService, never()).record(any(), any(), any(), any(), anyBoolean(), any());
    }

    private ChangeLoginPasswordCommand command(String current, String password, String confirmation) {
        return new ChangeLoginPasswordCommand(1L, "user01", current, password, confirmation, "127.0.0.1");
    }
}
