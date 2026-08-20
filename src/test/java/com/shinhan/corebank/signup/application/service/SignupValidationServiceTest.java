package com.shinhan.corebank.signup.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.common.exception.ErrorCode;
import com.shinhan.corebank.signup.application.port.in.ValidateSignupCommand;
import com.shinhan.corebank.signup.application.port.in.ValidateSignupResult;
import com.shinhan.corebank.signup.application.port.out.AccountAuthTokenPort;
import com.shinhan.corebank.signup.application.port.out.AuthTokenGeneratorPort;
import com.shinhan.corebank.signup.application.port.out.EmailVerificationTokenPort;
import com.shinhan.corebank.signup.application.port.out.SignupCustomerAvailabilityPort;
import com.shinhan.corebank.signup.application.port.out.SignupTokenTransitionPort;
import com.shinhan.corebank.signup.application.port.out.TempSignupTokenPort;
import com.shinhan.corebank.signup.application.port.out.TermsAuthTokenPort;
import com.shinhan.corebank.signup.application.port.out.UserIdCheckTokenPort;
import com.shinhan.corebank.signup.config.SignupTokenProperties;
import com.shinhan.corebank.signup.domain.exception.SignupErrorCode;
import com.shinhan.corebank.signup.domain.model.AccountAuthTokenPayload;
import com.shinhan.corebank.signup.domain.model.AgreedTerm;
import com.shinhan.corebank.signup.domain.model.EmailVerificationPurpose;
import com.shinhan.corebank.signup.domain.model.EmailVerificationTokenPayload;
import com.shinhan.corebank.signup.domain.model.TempSignupTokenPayload;
import com.shinhan.corebank.signup.domain.model.TermsAuthTokenPayload;
import com.shinhan.corebank.signup.domain.model.UserIdCheckTokenPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 회원가입 최초 검증과 정보수정 토큰 회전 정책을 검증한다.
@ExtendWith(MockitoExtension.class)
class SignupValidationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-20T01:00:00Z");
    private static final Duration TEMP_TTL = Duration.ofMinutes(30);

    @Mock TermsAuthTokenPort termsTokenPort;
    @Mock AccountAuthTokenPort accountTokenPort;
    @Mock UserIdCheckTokenPort userIdTokenPort;
    @Mock EmailVerificationTokenPort emailTokenPort;
    @Mock TempSignupTokenPort tempTokenPort;
    @Mock SignupTokenTransitionPort transitionPort;
    @Mock SignupCustomerAvailabilityPort availabilityPort;
    @Mock AuthTokenGeneratorPort tokenGeneratorPort;
    @Mock PasswordEncoder passwordEncoder;

    SignupValidationService service;

    @BeforeEach
    void setUp() {
        service = new SignupValidationService(
                termsTokenPort,
                accountTokenPort,
                userIdTokenPort,
                emailTokenPort,
                tempTokenPort,
                transitionPort,
                availabilityPort,
                tokenGeneratorPort,
                passwordEncoder,
                new SignupTokenProperties(
                        Duration.ofMinutes(30),
                        Duration.ofMinutes(3),
                        Duration.ofMinutes(30),
                        Duration.ofMinutes(10),
                        TEMP_TTL
                ),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("네 인증 토큰이 유효하면 1800초 tempSignupToken을 발급한다")
    void validatesInitialSignupAtomically() {
        ValidateSignupCommand command = initialCommand();
        givenInitialProofs(command);
        given(passwordEncoder.encode(command.userPassword()))
                .willReturn("bcrypt-hash");
        given(tokenGeneratorPort.generateTempSignupToken())
                .willReturn("TEMP_SIGNUP_new");
        given(transitionPort.replaceInitialTokensWithTemp(
                any(), any(), any(), any(), any(), any(), any()
        )).willReturn(true);

        ValidateSignupResult result = service.validate(command);

        assertThat(result.tempSignupToken()).isEqualTo("TEMP_SIGNUP_new");
        assertThat(result.expiresIn()).isEqualTo(1800L);
        ArgumentCaptor<TempSignupTokenPayload> payload =
                ArgumentCaptor.forClass(TempSignupTokenPayload.class);
        verify(transitionPort).replaceInitialTokensWithTemp(
                eq("TERMS"), eq("ACCOUNT"), eq("USER_ID"), eq("EMAIL"),
                eq("TEMP_SIGNUP_new"), payload.capture(), eq(TEMP_TTL)
        );
        assertThat(payload.getValue().passwordHash()).isEqualTo("bcrypt-hash");
        assertThat(payload.getValue().toString())
                .doesNotContain("Password123!");
    }

    @Test
    @DisplayName("약관 토큰이 없으면 ATH0104이고 어떤 토큰도 소비하지 않는다")
    void rejectsInvalidTermsToken() {
        BusinessException exception = catchThrowableOfType(
                () -> service.validate(initialCommand()),
                BusinessException.class
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(SignupErrorCode.INVALID_TERMS_AUTH_TOKEN);
        verify(transitionPort, never()).replaceInitialTokensWithTemp(
                any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    @DisplayName("계좌 토큰이 없으면 ATH0105이다")
    void rejectsInvalidAccountToken() {
        given(termsTokenPort.find("TERMS")).willReturn(Optional.of(terms()));

        assertError(initialCommand(), SignupErrorCode.INVALID_ACCOUNT_AUTH_TOKEN);
    }

    @Test
    @DisplayName("아이디 토큰이 없으면 ATH0005이다")
    void rejectsMissingUserIdToken() {
        given(termsTokenPort.find("TERMS")).willReturn(Optional.of(terms()));
        given(accountTokenPort.find("ACCOUNT")).willReturn(Optional.of(account()));

        assertError(initialCommand(), SignupErrorCode.USER_ID_CHECK_REQUIRED);
    }

    @Test
    @DisplayName("이메일 토큰이 없으면 ATH0103이다")
    void rejectsMissingEmailToken() {
        given(termsTokenPort.find("TERMS")).willReturn(Optional.of(terms()));
        given(accountTokenPort.find("ACCOUNT")).willReturn(Optional.of(account()));
        given(userIdTokenPort.find("USER_ID")).willReturn(Optional.of(
                userId("honggildong")
        ));

        assertError(
                initialCommand(),
                SignupErrorCode.INVALID_EMAIL_VERIFICATION_TOKEN
        );
    }

    @Test
    @DisplayName("아이디 토큰의 아이디가 다르면 ATH0005이다")
    void rejectsMismatchedUserIdProof() {
        ValidateSignupCommand command = initialCommand();
        given(termsTokenPort.find("TERMS")).willReturn(Optional.of(terms()));
        given(accountTokenPort.find("ACCOUNT")).willReturn(Optional.of(account()));
        given(userIdTokenPort.find("USER_ID")).willReturn(Optional.of(
                new UserIdCheckTokenPayload("another", LocalDateTime.now())
        ));
        given(emailTokenPort.find("EMAIL")).willReturn(Optional.of(email(
                "hong@corebank.example.com",
                EmailVerificationPurpose.SIGN_UP
        )));

        assertError(command, SignupErrorCode.USER_ID_CHECK_REQUIRED);
    }

    @Test
    @DisplayName("이메일 또는 목적이 다르면 ATH0103이다")
    void rejectsMismatchedEmailProof() {
        ValidateSignupCommand command = initialCommand();
        given(termsTokenPort.find("TERMS")).willReturn(Optional.of(terms()));
        given(accountTokenPort.find("ACCOUNT")).willReturn(Optional.of(account()));
        given(userIdTokenPort.find("USER_ID")).willReturn(Optional.of(userId(
                "honggildong"
        )));
        given(emailTokenPort.find("EMAIL")).willReturn(Optional.of(email(
                "hong@corebank.example.com",
                EmailVerificationPurpose.EMAIL_CHANGE
        )));

        assertError(command, SignupErrorCode.INVALID_EMAIL_VERIFICATION_TOKEN);
    }

    @Test
    @DisplayName("비밀번호 확인값이 다르면 ATH0002이다")
    void rejectsPasswordConfirmationMismatch() {
        ValidateSignupCommand source = initialCommand();
        ValidateSignupCommand command = new ValidateSignupCommand(
                source.termsAuthToken(), source.accountAuthToken(),
                source.userIdCheckToken(), source.emailVerificationToken(), null,
                source.userId(), source.userPassword(), "Password456!",
                source.email(), source.phoneNumber()
        );

        assertError(command, SignupErrorCode.PASSWORD_CONFIRMATION_MISMATCH);
    }

    @Test
    @DisplayName("영문 숫자 특수문자 조합이 아니면 ATH0001이다")
    void rejectsInvalidPasswordComposition() {
        ValidateSignupCommand source = initialCommand();
        ValidateSignupCommand command = new ValidateSignupCommand(
                source.termsAuthToken(), source.accountAuthToken(),
                source.userIdCheckToken(), source.emailVerificationToken(), null,
                source.userId(), "PasswordOnly", "PasswordOnly",
                source.email(), source.phoneNumber()
        );

        assertError(command, SignupErrorCode.INVALID_PASSWORD_FORMAT);
    }

    @Test
    @DisplayName("휴대폰번호가 숫자 11자리가 아니면 CMN0001이다")
    void rejectsInvalidPhoneNumber() {
        ValidateSignupCommand source = initialCommand();
        ValidateSignupCommand command = new ValidateSignupCommand(
                source.termsAuthToken(), source.accountAuthToken(),
                source.userIdCheckToken(), source.emailVerificationToken(), null,
                source.userId(), source.userPassword(),
                source.userPasswordConfirm(), source.email(), "010-1234-5678"
        );

        assertError(command, CommonErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("아이디 형식을 다시 검사해 위반하면 ATH0004이다")
    void rejectsInvalidUserIdFormat() {
        ValidateSignupCommand source = initialCommand();
        ValidateSignupCommand command = new ValidateSignupCommand(
                source.termsAuthToken(), source.accountAuthToken(),
                source.userIdCheckToken(), source.emailVerificationToken(), null,
                "Invalid-ID", source.userPassword(),
                source.userPasswordConfirm(), source.email(),
                source.phoneNumber()
        );

        assertError(command, SignupErrorCode.INVALID_USER_ID_FORMAT);
        verify(termsTokenPort, never()).find(any());
    }

    @Test
    @DisplayName("이메일 형식을 다시 검사해 위반하면 CMN0001이다")
    void rejectsInvalidEmailFormat() {
        ValidateSignupCommand source = initialCommand();
        ValidateSignupCommand command = new ValidateSignupCommand(
                source.termsAuthToken(), source.accountAuthToken(),
                source.userIdCheckToken(), source.emailVerificationToken(), null,
                source.userId(), source.userPassword(),
                source.userPasswordConfirm(), "invalid-email",
                source.phoneNumber()
        );

        assertError(command, CommonErrorCode.INVALID_INPUT);
        verify(termsTokenPort, never()).find(any());
    }

    @Test
    @DisplayName("아이디와 이메일은 토큰 검증 후 현재 중복 여부를 다시 확인한다")
    void rechecksCurrentAvailability() {
        ValidateSignupCommand command = initialCommand();
        givenInitialProofs(command);
        given(availabilityPort.isUserIdTaken(command.userId())).willReturn(true);

        assertError(command, SignupErrorCode.DUPLICATE_USER_ID);
        verify(transitionPort, never()).replaceInitialTokensWithTemp(
                any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    @DisplayName("Redis 전환 실패는 성공 응답으로 바뀌지 않는다")
    void propagatesRedisTransitionFailure() {
        ValidateSignupCommand command = initialCommand();
        givenInitialProofs(command);
        given(passwordEncoder.encode(command.userPassword())).willReturn("hash");
        given(tokenGeneratorPort.generateTempSignupToken())
                .willReturn("TEMP_SIGNUP_new");
        given(transitionPort.replaceInitialTokensWithTemp(
                any(), any(), any(), any(), any(), any(), any()
        )).willThrow(new IllegalStateException("redis down"));

        assertThatThrownBy(() -> service.validate(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("redis down");
    }

    @Test
    @DisplayName("같은 아이디와 이메일 수정은 기존 temp 토큰만 회전한다")
    void rotatesTempTokenForPasswordAndPhoneEdit() {
        ValidateSignupCommand command = editCommand(
                "honggildong", "hong@corebank.example.com", null, null
        );
        given(tempTokenPort.find("TEMP_SIGNUP_old"))
                .willReturn(Optional.of(tempPayload()));
        given(passwordEncoder.encode(command.userPassword()))
                .willReturn("new-hash");
        given(tokenGeneratorPort.generateTempSignupToken())
                .willReturn("TEMP_SIGNUP_new");
        given(transitionPort.rotateTempToken(
                any(), any(), any(), any(), any(), any()
        )).willReturn(true);

        service.validate(command);

        verify(transitionPort).rotateTempToken(
                eq("TEMP_SIGNUP_old"), eq(null), eq(null),
                eq("TEMP_SIGNUP_new"), any(), eq(TEMP_TTL)
        );
    }

    @Test
    @DisplayName("아이디 변경 시 새 아이디 중복확인 토큰이 필수다")
    void requiresUserIdTokenWhenEdited() {
        ValidateSignupCommand command = editCommand(
                "newuser", "hong@corebank.example.com", null, null
        );
        given(tempTokenPort.find("TEMP_SIGNUP_old"))
                .willReturn(Optional.of(tempPayload()));

        assertError(command, SignupErrorCode.USER_ID_CHECK_REQUIRED);
        verify(tempTokenPort, never()).consume("TEMP_SIGNUP_old");
        verify(transitionPort, never()).rotateTempToken(
                any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    @DisplayName("이메일 변경 시 새 이메일 인증 토큰을 함께 소비한다")
    void consumesEmailTokenWhenEdited() {
        ValidateSignupCommand command = editCommand(
                "honggildong", "new@corebank.example.com", null, "NEW_EMAIL"
        );
        given(tempTokenPort.find("TEMP_SIGNUP_old"))
                .willReturn(Optional.of(tempPayload()));
        given(emailTokenPort.find("NEW_EMAIL")).willReturn(Optional.of(email(
                "new@corebank.example.com",
                EmailVerificationPurpose.SIGN_UP
        )));
        given(passwordEncoder.encode(command.userPassword())).willReturn("hash");
        given(tokenGeneratorPort.generateTempSignupToken())
                .willReturn("TEMP_SIGNUP_new");
        given(transitionPort.rotateTempToken(
                any(), any(), any(), any(), any(), any()
        )).willReturn(true);

        service.validate(command);

        verify(transitionPort).rotateTempToken(
                eq("TEMP_SIGNUP_old"), eq(null), eq("NEW_EMAIL"),
                eq("TEMP_SIGNUP_new"), any(), eq(TEMP_TTL)
        );
    }

    @Test
    @DisplayName("동시 수정으로 원자 전환이 실패하면 CMN0001이다")
    void rejectsLostTokenRotation() {
        ValidateSignupCommand command = editCommand(
                "honggildong", "hong@corebank.example.com", null, null
        );
        given(tempTokenPort.find("TEMP_SIGNUP_old"))
                .willReturn(Optional.of(tempPayload()));
        given(passwordEncoder.encode(any())).willReturn("hash");
        given(tokenGeneratorPort.generateTempSignupToken())
                .willReturn("TEMP_SIGNUP_new");
        given(transitionPort.rotateTempToken(
                any(), any(), any(), any(), any(), any()
        )).willReturn(false);

        assertError(command, CommonErrorCode.INVALID_INPUT);
    }

    private void givenInitialProofs(ValidateSignupCommand command) {
        given(termsTokenPort.find(command.termsAuthToken()))
                .willReturn(Optional.of(terms()));
        given(accountTokenPort.find(command.accountAuthToken()))
                .willReturn(Optional.of(account()));
        given(userIdTokenPort.find(command.userIdCheckToken()))
                .willReturn(Optional.of(userId(command.userId())));
        given(emailTokenPort.find(command.emailVerificationToken()))
                .willReturn(Optional.of(email(
                        command.email(), EmailVerificationPurpose.SIGN_UP
                )));
    }

    private ValidateSignupCommand initialCommand() {
        return new ValidateSignupCommand(
                "TERMS", "ACCOUNT", "USER_ID", "EMAIL", null,
                "honggildong", "Password123!", "Password123!",
                "hong@corebank.example.com", "01012345678"
        );
    }

    private ValidateSignupCommand editCommand(
            String userId,
            String email,
            String userIdToken,
            String emailToken
    ) {
        return new ValidateSignupCommand(
                null, null, userIdToken, emailToken, "TEMP_SIGNUP_old",
                userId, "NewPassword123!", "NewPassword123!",
                email, "01098765432"
        );
    }

    private TermsAuthTokenPayload terms() {
        return new TermsAuthTokenPayload(
                List.of(new AgreedTerm("SIGNUP_TERMS", "1.0")), NOW
        );
    }

    private AccountAuthTokenPayload account() {
        return new AccountAuthTokenPayload(
                "BANK_CUSTOMER_001", "BANK_ACCOUNT_001", NOW
        );
    }

    private UserIdCheckTokenPayload userId(String value) {
        return new UserIdCheckTokenPayload(
                value, LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)
        );
    }

    private EmailVerificationTokenPayload email(
            String value,
            EmailVerificationPurpose purpose
    ) {
        return new EmailVerificationTokenPayload(
                value, purpose, LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)
        );
    }

    private TempSignupTokenPayload tempPayload() {
        return new TempSignupTokenPayload(
                terms().agreedTerms(), "BANK_CUSTOMER_001", "BANK_ACCOUNT_001",
                "honggildong", "old-hash", "hong@corebank.example.com",
                "01012345678", NOW
        );
    }

    private void assertError(
            ValidateSignupCommand command,
            ErrorCode expectedErrorCode
    ) {
        BusinessException exception = catchThrowableOfType(
                () -> service.validate(command), BusinessException.class
        );
        assertThat(exception.getErrorCode()).isEqualTo(expectedErrorCode);
    }
}
