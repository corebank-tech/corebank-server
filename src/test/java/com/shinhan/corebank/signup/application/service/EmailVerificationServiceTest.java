package com.shinhan.corebank.signup.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.signup.application.port.in.IssueEmailVerificationCommand;
import com.shinhan.corebank.signup.application.port.in.IssueEmailVerificationResult;
import com.shinhan.corebank.signup.application.port.in.VerifyEmailCommand;
import com.shinhan.corebank.signup.application.port.in.VerifyEmailResult;
import com.shinhan.corebank.signup.application.port.out.AuthTokenGeneratorPort;
import com.shinhan.corebank.signup.application.port.out.EmailVerificationCodeGeneratorPort;
import com.shinhan.corebank.signup.application.port.out.EmailVerificationRequestPort;
import com.shinhan.corebank.signup.application.port.out.EmailVerificationTokenPort;
import com.shinhan.corebank.signup.application.port.out.SignupCustomerAvailabilityPort;
import com.shinhan.corebank.signup.application.port.out.VerificationRequestIdGeneratorPort;
import com.shinhan.corebank.signup.config.EmailVerificationProperties;
import com.shinhan.corebank.signup.config.SignupTokenProperties;
import com.shinhan.corebank.signup.domain.exception.SignupErrorCode;
import com.shinhan.corebank.signup.domain.model.EmailVerificationPurpose;
import com.shinhan.corebank.signup.domain.model.EmailVerificationRequest;
import com.shinhan.corebank.signup.domain.model.EmailVerificationTokenPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    private static final String REQUEST_ID = "EVF_test-request";
    private static final String CODE = "012345";
    private static final String TOKEN = "EMAIL_VERIFICATION_test-token";
    private static final Duration CODE_TTL = Duration.ofMinutes(3);
    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);

    @Mock SignupCustomerAvailabilityPort customerAvailabilityPort;
    @Mock EmailVerificationRequestPort requestPort;
    @Mock EmailVerificationTokenPort tokenPort;
    @Mock VerificationRequestIdGeneratorPort requestIdGeneratorPort;
    @Mock EmailVerificationCodeGeneratorPort codeGeneratorPort;
    @Mock AuthTokenGeneratorPort authTokenGeneratorPort;

    PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    Clock clock;
    EmailVerificationService service;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(
                Instant.parse("2026-08-19T01:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );
        service = new EmailVerificationService(
                customerAvailabilityPort,
                requestPort,
                tokenPort,
                requestIdGeneratorPort,
                codeGeneratorPort,
                authTokenGeneratorPort,
                passwordEncoder,
                new EmailVerificationProperties(CODE_TTL),
                new SignupTokenProperties(
                        Duration.ofMinutes(30),
                        Duration.ofMinutes(3),
                        TOKEN_TTL,
                        Duration.ofMinutes(10),
                        Duration.ofMinutes(30)
                ),
                clock
        );
    }

    @Test
    @DisplayName("인증번호를 180초 동안 발급하고 DB에는 BCrypt 해시만 저장한다")
    void issuesHashedVerificationCode() {
        given(requestIdGeneratorPort.generateEmailVerificationId())
                .willReturn(REQUEST_ID);
        given(codeGeneratorPort.generate()).willReturn(CODE);

        IssueEmailVerificationResult result = service.issue(
                new IssueEmailVerificationCommand(
                        "User@Example.com",
                        EmailVerificationPurpose.SIGN_UP
                )
        );

        assertThat(result.emailVerificationId()).isEqualTo(REQUEST_ID);
        assertThat(result.verificationCode()).isEqualTo(CODE);
        assertThat(result.expiresIn()).isEqualTo(180L);
        verify(requestPort).invalidateActive(
                "user@example.com",
                EmailVerificationPurpose.SIGN_UP
        );

        ArgumentCaptor<EmailVerificationRequest> request =
                ArgumentCaptor.forClass(EmailVerificationRequest.class);
        verify(requestPort).save(request.capture());
        assertThat(request.getValue().codeHash()).isNotEqualTo(CODE);
        assertThat(passwordEncoder.matches(CODE, request.getValue().codeHash()))
                .isTrue();
        assertThat(request.getValue().expiresAt())
                .isEqualTo(LocalDateTime.now(clock).plusMinutes(3));
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 ATH0302를 반환한다")
    void rejectsDuplicateEmail() {
        given(customerAvailabilityPort.isEmailTaken("user@example.com"))
                .willReturn(true);

        BusinessException exception = catchThrowableOfType(
                () -> service.issue(new IssueEmailVerificationCommand(
                        "user@example.com",
                        EmailVerificationPurpose.SIGN_UP
                )),
                BusinessException.class
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(SignupErrorCode.DUPLICATE_EMAIL);
        verify(requestPort, never()).save(any());
    }

    @Test
    @DisplayName("이메일 형식이 잘못되면 CMN0001을 반환한다")
    void rejectsInvalidEmailFormat() {
        BusinessException exception = catchThrowableOfType(
                () -> service.issue(new IssueEmailVerificationCommand(
                        "not-email",
                        EmailVerificationPurpose.SIGN_UP
                )),
                BusinessException.class
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_INPUT);
        verify(requestPort, never()).save(any());
    }

    @Test
    @DisplayName("올바른 인증번호면 요청을 사용 처리하고 1800초 토큰을 발급한다")
    void verifiesCodeAndIssuesToken() {
        EmailVerificationRequest request = activeRequest(CODE, 3);
        given(requestPort.findByIdForUpdate(REQUEST_ID))
                .willReturn(Optional.of(request));
        given(authTokenGeneratorPort.generateEmailVerificationToken())
                .willReturn(TOKEN);

        VerifyEmailResult result = service.verify(
                new VerifyEmailCommand(REQUEST_ID, CODE)
        );

        assertThat(request.used()).isTrue();
        assertThat(request.verifiedAt()).isEqualTo(LocalDateTime.now(clock));
        assertThat(result.emailVerificationToken()).isEqualTo(TOKEN);
        assertThat(result.verifiedAt().getOffset().getTotalSeconds())
                .isEqualTo(9 * 60 * 60);

        ArgumentCaptor<EmailVerificationTokenPayload> payload =
                ArgumentCaptor.forClass(EmailVerificationTokenPayload.class);
        verify(tokenPort).save(
                org.mockito.ArgumentMatchers.eq(TOKEN),
                payload.capture(),
                org.mockito.ArgumentMatchers.eq(TOKEN_TTL)
        );
        assertThat(payload.getValue().email()).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("없는 요청과 이미 사용한 요청은 ATH0202를 반환한다")
    void rejectsMissingOrUsedRequest() {
        given(requestPort.findByIdForUpdate("missing"))
                .willReturn(Optional.empty());
        assertError(
                new VerifyEmailCommand("missing", CODE),
                SignupErrorCode.EMAIL_VERIFICATION_REQUEST_NOT_FOUND
        );

        EmailVerificationRequest used = activeRequest(CODE, 3);
        used.verify(LocalDateTime.now(clock));
        given(requestPort.findByIdForUpdate(REQUEST_ID))
                .willReturn(Optional.of(used));
        assertError(
                new VerifyEmailCommand(REQUEST_ID, CODE),
                SignupErrorCode.EMAIL_VERIFICATION_REQUEST_NOT_FOUND
        );
    }

    @Test
    @DisplayName("만료된 인증번호는 ATH0008을 반환한다")
    void rejectsExpiredCode() {
        given(requestPort.findByIdForUpdate(REQUEST_ID))
                .willReturn(Optional.of(activeRequest(CODE, 0)));

        assertError(
                new VerifyEmailCommand(REQUEST_ID, CODE),
                SignupErrorCode.EMAIL_VERIFICATION_EXPIRED
        );
    }

    @Test
    @DisplayName("인증번호 형식 오류와 불일치는 ATH0007을 반환한다")
    void rejectsMalformedOrMismatchedCode() {
        given(requestPort.findByIdForUpdate(REQUEST_ID))
                .willReturn(Optional.of(activeRequest(CODE, 3)));
        assertError(
                new VerifyEmailCommand(REQUEST_ID, "12345"),
                SignupErrorCode.EMAIL_VERIFICATION_CODE_MISMATCH
        );

        given(requestPort.findByIdForUpdate(REQUEST_ID))
                .willReturn(Optional.of(activeRequest(CODE, 3)));
        assertError(
                new VerifyEmailCommand(REQUEST_ID, "999999"),
                SignupErrorCode.EMAIL_VERIFICATION_CODE_MISMATCH
        );
        verify(tokenPort, never()).save(any(), any(), any());
    }

    private EmailVerificationRequest activeRequest(
            String code,
            long remainingMinutes
    ) {
        LocalDateTime now = LocalDateTime.now(clock);
        return EmailVerificationRequest.issue(
                REQUEST_ID,
                EmailVerificationPurpose.SIGN_UP,
                "user@example.com",
                passwordEncoder.encode(code),
                now.plusMinutes(remainingMinutes),
                now.minusMinutes(1)
        );
    }

    private void assertError(
            VerifyEmailCommand command,
            SignupErrorCode expected
    ) {
        BusinessException exception = catchThrowableOfType(
                () -> service.verify(command),
                BusinessException.class
        );
        assertThat(exception.getErrorCode()).isEqualTo(expected);
    }
}
