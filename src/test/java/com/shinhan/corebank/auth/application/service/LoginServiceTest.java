package com.shinhan.corebank.auth.application.service;

import com.shinhan.corebank.auth.application.port.in.LoginCommand;
import com.shinhan.corebank.auth.application.port.in.LoginResult;
import com.shinhan.corebank.auth.application.port.out.LoginCustomerPort;
import com.shinhan.corebank.auth.application.port.out.LoginFailureUpdateResult;
import com.shinhan.corebank.auth.application.port.out.LoginSuccessUpdateResult;
import com.shinhan.corebank.auth.application.port.out.PasswordHashVerifierPort;
import com.shinhan.corebank.auth.application.port.out.RecordLoginAuditPort;
import com.shinhan.corebank.auth.domain.exception.AuthErrorCode;
import com.shinhan.corebank.auth.domain.exception.LoginFailedException;
import com.shinhan.corebank.auth.domain.model.LoginAttemptResult;
import com.shinhan.corebank.auth.domain.model.LoginAuditReason;
import com.shinhan.corebank.auth.domain.model.LoginCustomer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("로그인 서비스 단위 테스트")
class LoginServiceTest {

    private static final String REQUEST_IP = "192.168.0.10";
    private static final String RAW_PASSWORD = "CorrectPassword1!";
    private static final String PASSWORD_HASH = "bcrypt-password-hash";
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);
    private static final LocalDateTime LOGIN_AT =
            LocalDateTime.of(2026, 8, 15, 10, 30);

    @Mock
    private LoginCustomerPort loginCustomerPort;

    @Mock
    private PasswordHashVerifierPort passwordHashVerifierPort;

    @Mock
    private LoginAttemptProcessor loginAttemptProcessor;

    @Mock
    private LoginSuccessProcessor loginSuccessProcessor;

    @Mock
    private RecordLoginAuditPort recordLoginAuditPort;

    private LoginService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-15T01:30:00Z"),
                KST
        );

        service = new LoginService(
                loginCustomerPort,
                passwordHashVerifierPort,
                loginAttemptProcessor,
                loginSuccessProcessor,
                recordLoginAuditPort,
                clock
        );
    }

    // 존재하지 않는 아이디는 실패 횟수 없이 동일한 인증 오류를 반환
    @Test
    @DisplayName("고객이 없으면 비밀번호를 검증하지 않고 ATH0101을 반환한다")
    void customerNotFound() {
        LoginCommand command = loginCommand();
        given(loginCustomerPort.findByUserId(command.userId()))
                .willReturn(Optional.empty());

        LoginFailedException exception = catchThrowableOfType(
                () -> service.login(command),
                LoginFailedException.class
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(AuthErrorCode.LOGIN_FAILED);
        assertThat(exception.getAttemptResult()).isEmpty();
        verify(passwordHashVerifierPort, never())
                .matches(anyString(), anyString());
        verify(loginCustomerPort, never())
                .recordLoginFailure(1L);
        verify(recordLoginAuditPort).record(
                null,
                REQUEST_IP,
                false,
                LoginAuditReason.INVALID_CREDENTIALS
        );
    }

    // 이미 잠긴 계정은 비밀번호 검증과 실패 횟수 갱신을 생략
    @Test
    @DisplayName("이미 잠긴 고객은 비밀번호를 검증하지 않고 ATH0102를 반환한다")
    void accountAlreadyLocked() {
        LoginCommand command = loginCommand();
        given(loginCustomerPort.findByUserId(command.userId()))
                .willReturn(Optional.of(loginCustomer(5, true)));

        LoginFailedException exception = catchThrowableOfType(
                () -> service.login(command),
                LoginFailedException.class
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(AuthErrorCode.ACCOUNT_LOCKED);
        assertThat(exception.getAttemptResult()).isEmpty();
        verify(passwordHashVerifierPort, never())
                .matches(anyString(), anyString());
        verify(loginCustomerPort, never())
                .recordLoginFailure(1L);
        verify(recordLoginAuditPort).record(
                1L,
                REQUEST_IP,
                false,
                LoginAuditReason.ACCOUNT_LOCKED
        );
    }

    // 비밀번호가 일치하면 로그인 상태와 성공 감사를 기록
    @Test
    @DisplayName("올바른 비밀번호는 로그인 성공 상태를 저장하고 결과를 반환한다")
    void loginSuccess() {
        LoginCommand command = loginCommand();
        given(loginCustomerPort.findByUserId(command.userId()))
                .willReturn(Optional.of(loginCustomer(2, false)));
        given(passwordHashVerifierPort.matches(
                RAW_PASSWORD,
                PASSWORD_HASH
        )).willReturn(true);
        given(loginSuccessProcessor.process(
                1L,
                LOGIN_AT,
                REQUEST_IP
        )).willReturn(LoginSuccessUpdateResult.COMPLETED);

        LoginResult result = service.login(command);

        assertThat(result).isEqualTo(
                new LoginResult(1L, "user01", "홍길동")
        );
        verify(loginSuccessProcessor).process(
                1L,
                LOGIN_AT,
                REQUEST_IP
        );
        verify(loginCustomerPort, never())
                .recordLoginFailure(1L);
    }

    // 비밀번호 검증 뒤 계정이 잠기면 성공 처리 대신 잠금 오류 반환
    @Test
    @DisplayName("로그인 성공 상태 저장 전에 계정이 잠기면 ATH0102를 반환한다")
    void accountLocksBeforeLoginSuccessStateUpdate() {
        LoginCommand command = loginCommand();
        given(loginCustomerPort.findByUserId(command.userId()))
                .willReturn(Optional.of(loginCustomer(4, false)));
        given(passwordHashVerifierPort.matches(
                RAW_PASSWORD,
                PASSWORD_HASH
        )).willReturn(true);
        given(loginSuccessProcessor.process(
                1L,
                LOGIN_AT,
                REQUEST_IP
        )).willReturn(LoginSuccessUpdateResult.ACCOUNT_LOCKED);

        LoginFailedException exception = catchThrowableOfType(
                () -> service.login(command),
                LoginFailedException.class
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(AuthErrorCode.ACCOUNT_LOCKED);
        assertThat(exception.getAttemptResult()).isEmpty();
        verify(recordLoginAuditPort).record(
                1L,
                REQUEST_IP,
                false,
                LoginAuditReason.ACCOUNT_LOCKED
        );
    }

    // 잠금 전 비밀번호 불일치는 저장된 최신 횟수와 잔여 횟수를 노출
    @Test
    @DisplayName("비밀번호 불일치 4회는 최신 횟수와 잔여 횟수로 ATH0101을 반환한다")
    void invalidCredentialsBeforeLock() {
        LoginCommand command = loginCommand();
        LoginAttemptResult attemptResult =
                new LoginAttemptResult(4, 1);
        given(loginCustomerPort.findByUserId(command.userId()))
                .willReturn(Optional.of(loginCustomer(3, false)));
        given(passwordHashVerifierPort.matches(
                RAW_PASSWORD,
                PASSWORD_HASH
        )).willReturn(false);
        given(loginCustomerPort.recordLoginFailure(1L))
                .willReturn(new LoginFailureUpdateResult(4, false));
        given(loginAttemptProcessor.process(4))
                .willReturn(attemptResult);

        LoginFailedException exception = catchThrowableOfType(
                () -> service.login(command),
                LoginFailedException.class
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(AuthErrorCode.LOGIN_FAILED);
        assertThat(exception.getAttemptResult())
                .contains(attemptResult);
        verify(loginAttemptProcessor).process(4);
        verify(loginSuccessProcessor, never()).process(
                1L,
                LOGIN_AT,
                REQUEST_IP
        );
        verify(recordLoginAuditPort).record(
                1L,
                REQUEST_IP,
                false,
                LoginAuditReason.INVALID_CREDENTIALS
        );
    }

    // 다섯 번째 실패는 횟수 데이터를 노출하지 않고 계정 잠금으로 처리
    @Test
    @DisplayName("비밀번호 불일치가 5회에 도달하면 데이터 없이 ATH0102를 반환한다")
    void locksAccountOnFifthFailure() {
        LoginCommand command = loginCommand();
        given(loginCustomerPort.findByUserId(command.userId()))
                .willReturn(Optional.of(loginCustomer(4, false)));
        given(passwordHashVerifierPort.matches(
                RAW_PASSWORD,
                PASSWORD_HASH
        )).willReturn(false);
        given(loginCustomerPort.recordLoginFailure(1L))
                .willReturn(new LoginFailureUpdateResult(5, true));

        LoginFailedException exception = catchThrowableOfType(
                () -> service.login(command),
                LoginFailedException.class
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(AuthErrorCode.ACCOUNT_LOCKED);
        assertThat(exception.getAttemptResult()).isEmpty();
        verify(loginAttemptProcessor, never()).process(5);
        verify(recordLoginAuditPort).record(
                1L,
                REQUEST_IP,
                false,
                LoginAuditReason.ACCOUNT_LOCKED
        );
    }

    // 감사 저장 장애가 비밀번호 불일치 오류를 내부 오류로 바꾸지 않음
    @Test
    @DisplayName("실패 감사 저장에 실패해도 ATH0101과 시도 결과를 유지한다")
    void preservesLoginFailureWhenAuditRecordingFails() {
        LoginCommand command = loginCommand();
        LoginAttemptResult attemptResult =
                new LoginAttemptResult(4, 1);
        given(loginCustomerPort.findByUserId(command.userId()))
                .willReturn(Optional.of(loginCustomer(3, false)));
        given(passwordHashVerifierPort.matches(
                RAW_PASSWORD,
                PASSWORD_HASH
        )).willReturn(false);
        given(loginCustomerPort.recordLoginFailure(1L))
                .willReturn(new LoginFailureUpdateResult(4, false));
        given(loginAttemptProcessor.process(4))
                .willReturn(attemptResult);
        doThrow(new IllegalStateException("audit unavailable"))
                .when(recordLoginAuditPort)
                .record(
                        1L,
                        REQUEST_IP,
                        false,
                        LoginAuditReason.INVALID_CREDENTIALS
                );

        LoginFailedException exception = catchThrowableOfType(
                () -> service.login(command),
                LoginFailedException.class
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(AuthErrorCode.LOGIN_FAILED);
        assertThat(exception.getAttemptResult())
                .contains(attemptResult);
    }

    // 감사 저장 장애가 계정 잠금 오류를 내부 오류로 바꾸지 않음
    @Test
    @DisplayName("실패 감사 저장에 실패해도 ATH0102를 유지한다")
    void preservesAccountLockedFailureWhenAuditRecordingFails() {
        LoginCommand command = loginCommand();
        given(loginCustomerPort.findByUserId(command.userId()))
                .willReturn(Optional.of(loginCustomer(5, true)));
        doThrow(new IllegalStateException("audit unavailable"))
                .when(recordLoginAuditPort)
                .record(
                        1L,
                        REQUEST_IP,
                        false,
                        LoginAuditReason.ACCOUNT_LOCKED
                );

        LoginFailedException exception = catchThrowableOfType(
                () -> service.login(command),
                LoginFailedException.class
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(AuthErrorCode.ACCOUNT_LOCKED);
        assertThat(exception.getAttemptResult()).isEmpty();
    }

    private LoginCommand loginCommand() {
        return new LoginCommand(
                "user01",
                RAW_PASSWORD,
                REQUEST_IP
        );
    }

    private LoginCustomer loginCustomer(
            int failureCount,
            boolean accountLocked
    ) {
        return new LoginCustomer(
                1L,
                "user01",
                PASSWORD_HASH,
                "홍길동",
                failureCount,
                accountLocked
        );
    }
}
