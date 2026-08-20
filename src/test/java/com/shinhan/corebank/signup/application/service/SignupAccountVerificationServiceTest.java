package com.shinhan.corebank.signup.application.service;

import com.shinhan.corebank.signup.application.port.in.VerifySignupAccountCommand;
import com.shinhan.corebank.signup.application.port.in.VerifySignupAccountResult;
import com.shinhan.corebank.signup.application.port.out.AccountAuthTokenPort;
import com.shinhan.corebank.signup.application.port.out.AuthTokenGeneratorPort;
import com.shinhan.corebank.signup.application.port.out.ExistingBankCustomerVerificationPort;
import com.shinhan.corebank.signup.config.SignupTokenProperties;
import com.shinhan.corebank.signup.domain.exception.AccountVerificationFailedException;
import com.shinhan.corebank.signup.domain.exception.SignupErrorCode;
import com.shinhan.corebank.signup.domain.model.AccountAuthTokenPayload;
import com.shinhan.corebank.signup.domain.model.ExistingBankAccountVerification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 실명·계좌 인증 서비스의 토큰 발급과 실패 분기를 검증한다.
@ExtendWith(MockitoExtension.class)
class SignupAccountVerificationServiceTest {

    private static final String TOKEN = "ACCOUNT_AUTH_test-token";
    private static final Duration ACCOUNT_AUTH_TTL = Duration.ofMinutes(10);
    private static final Instant NOW = Instant.parse("2026-08-20T01:00:00Z");
    private static final VerifySignupAccountCommand COMMAND =
            new VerifySignupAccountCommand(
                    "홍길동",
                    "900101",
                    "110123456789",
                    "1234"
            );

    @Mock ExistingBankCustomerVerificationPort verificationPort;
    @Mock AccountAuthTokenPort accountAuthTokenPort;
    @Mock AuthTokenGeneratorPort authTokenGeneratorPort;

    SignupAccountVerificationService service;

    @BeforeEach
    void setUp() {
        service = new SignupAccountVerificationService(
                verificationPort,
                accountAuthTokenPort,
                authTokenGeneratorPort,
                new SignupTokenProperties(
                        Duration.ofMinutes(30),
                        Duration.ofMinutes(3),
                        Duration.ofMinutes(30),
                        ACCOUNT_AUTH_TTL
                ),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("실명과 계좌가 일치하면 600초 accountAuthToken을 발급한다")
    void issuesAccountAuthToken() {
        givenVerification(ExistingBankAccountVerification.verified(
                "BANK_CUSTOMER_001",
                "BANK_ACCOUNT_001"
        ));
        given(authTokenGeneratorPort.generateAccountAuthToken())
                .willReturn(TOKEN);

        VerifySignupAccountResult result = service.verify(COMMAND);

        assertThat(result.accountAuthToken()).isEqualTo(TOKEN);
        assertThat(result.expiresIn()).isEqualTo(600L);

        ArgumentCaptor<AccountAuthTokenPayload> payload =
                ArgumentCaptor.forClass(AccountAuthTokenPayload.class);
        verify(accountAuthTokenPort).save(
                eq(TOKEN),
                payload.capture(),
                eq(ACCOUNT_AUTH_TTL)
        );
        assertThat(payload.getValue().existingBankCustomerId())
                .isEqualTo("BANK_CUSTOMER_001");
        assertThat(payload.getValue().verifiedBankAccountId())
                .isEqualTo("BANK_ACCOUNT_001");
        assertThat(payload.getValue().verifiedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("고객 또는 계좌 정보 불일치는 ATH0009이고 횟수를 노출하지 않는다")
    void rejectsInformationMismatchWithoutAttempts() {
        givenVerification(
                ExistingBankAccountVerification.informationMismatch()
        );

        AccountVerificationFailedException exception =
                catchThrowableOfType(
                        () -> service.verify(COMMAND),
                        AccountVerificationFailedException.class
                );

        assertThat(exception.getErrorCode())
                .isEqualTo(SignupErrorCode.ACCOUNT_VERIFICATION_FAILED);
        assertThat(exception.getAttemptResult()).isEmpty();
        verify(authTokenGeneratorPort, never()).generateAccountAuthToken();
        verify(accountAuthTokenPort, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("계좌비밀번호 불일치는 ATH0009와 실패 횟수를 반환한다")
    void rejectsPasswordMismatchWithAttempts() {
        givenVerification(
                ExistingBankAccountVerification.passwordMismatch(3)
        );

        AccountVerificationFailedException exception =
                catchThrowableOfType(
                        () -> service.verify(COMMAND),
                        AccountVerificationFailedException.class
                );

        assertThat(exception.getErrorCode())
                .isEqualTo(SignupErrorCode.ACCOUNT_VERIFICATION_FAILED);
        assertThat(exception.getAttemptResult()).hasValueSatisfying(result -> {
            assertThat(result.errorCount()).isEqualTo(3);
            assertThat(result.remainingAttempts()).isEqualTo(2);
        });
        verify(authTokenGeneratorPort, never()).generateAccountAuthToken();
        verify(accountAuthTokenPort, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("거래정지 계좌는 ATH0102와 5회 실패 결과를 반환한다")
    void rejectsLockedAccount() {
        givenVerification(ExistingBankAccountVerification.locked());

        AccountVerificationFailedException exception =
                catchThrowableOfType(
                        () -> service.verify(COMMAND),
                        AccountVerificationFailedException.class
                );

        assertThat(exception.getErrorCode())
                .isEqualTo(SignupErrorCode.ACCOUNT_LOCKED);
        assertThat(exception.getAttemptResult()).hasValueSatisfying(result -> {
            assertThat(result.errorCount()).isEqualTo(5);
            assertThat(result.remainingAttempts()).isZero();
        });
        verify(authTokenGeneratorPort, never()).generateAccountAuthToken();
        verify(accountAuthTokenPort, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("Redis 저장 실패 시 유효하지 않은 토큰을 성공으로 반환하지 않는다")
    void doesNotReturnTokenWhenRedisSaveFails() {
        givenVerification(ExistingBankAccountVerification.verified(
                "BANK_CUSTOMER_001",
                "BANK_ACCOUNT_001"
        ));
        given(authTokenGeneratorPort.generateAccountAuthToken())
                .willReturn(TOKEN);
        org.mockito.Mockito.doThrow(new IllegalStateException("redis down"))
                .when(accountAuthTokenPort)
                .save(eq(TOKEN), any(), eq(ACCOUNT_AUTH_TTL));

        assertThatThrownBy(() -> service.verify(COMMAND))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("redis down");
    }

    private void givenVerification(
            ExistingBankAccountVerification verification
    ) {
        given(verificationPort.verify(
                COMMAND.userName(),
                COMMAND.birthDate(),
                COMMAND.accountNumber(),
                COMMAND.accountPassword()
        )).willReturn(verification);
    }
}
