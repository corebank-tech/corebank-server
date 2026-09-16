package com.shinhan.corebank.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.shinhan.corebank.account.application.port.in.VerifyAccountPasswordCommand;
import com.shinhan.corebank.account.application.port.in.VerifyAccountPasswordResult;
import com.shinhan.corebank.account.application.port.out.AccountPasswordAuthTokenGeneratorPort;
import com.shinhan.corebank.account.application.port.out.AccountPasswordAuthTokenStorePort;
import com.shinhan.corebank.account.config.AccountPasswordProperties;
import com.shinhan.corebank.account.domain.AccountPasswordAttemptResult;
import com.shinhan.corebank.account.domain.AccountPasswordAuthTokenPayload;
import com.shinhan.corebank.account.domain.exception.AccountPasswordErrorCode;
import com.shinhan.corebank.account.domain.exception.AccountPasswordVerificationFailedException;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

// 계좌비밀번호 결과에 따른 오류 응답과 인증 토큰 발급을 검증한다.
class AccountPasswordVerificationServiceTest {

    private final AccountPasswordVerificationProcessor processor = mock(AccountPasswordVerificationProcessor.class);
    private final AccountPasswordAuthTokenGeneratorPort generatorPort =
            mock(AccountPasswordAuthTokenGeneratorPort.class);
    private final AccountPasswordAuthTokenStorePort storePort = mock(AccountPasswordAuthTokenStorePort.class);
    private final AccountPasswordVerificationService service = new AccountPasswordVerificationService(
            processor, generatorPort, storePort, new AccountPasswordProperties(Duration.ofMinutes(5)));

    @Test
    @DisplayName("올바른 비밀번호는 고객·계좌 payload의 300초 인증 토큰을 발급한다")
    void issuesAccountPasswordAuthToken() {
        VerifyAccountPasswordCommand command = command("1234");
        given(processor.verify(1L, 101L, "1234")).willReturn(new AccountPasswordAttemptResult(101L, true, 0, 5, false));
        given(generatorPort.generate()).willReturn("account-auth-token");

        VerifyAccountPasswordResult result = service.verify(command);

        assertThat(result.matched()).isTrue();
        assertThat(result.accountPasswordAuthToken()).isEqualTo("account-auth-token");
        ArgumentCaptor<AccountPasswordAuthTokenPayload> payload =
                ArgumentCaptor.forClass(AccountPasswordAuthTokenPayload.class);
        verify(storePort)
                .save(
                        org.mockito.ArgumentMatchers.eq("account-auth-token"),
                        payload.capture(),
                        org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(5)));
        assertThat(payload.getValue()).isEqualTo(new AccountPasswordAuthTokenPayload(1L, 101L));
    }

    @Test
    @DisplayName("비밀번호 불일치는 APW0001과 최신 실패 횟수를 반환한다")
    void rejectsMismatchedPassword() {
        given(processor.verify(1L, 101L, "9999"))
                .willReturn(new AccountPasswordAttemptResult(101L, false, 2, 3, false));

        assertThatThrownBy(() -> service.verify(command("9999")))
                .isInstanceOfSatisfying(AccountPasswordVerificationFailedException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(AccountPasswordErrorCode.PASSWORD_MISMATCH);
                    assertThat(exception.getAttemptResult().errorCount()).isEqualTo(2);
                });
        verify(generatorPort, never()).generate();
    }

    @Test
    @DisplayName("다섯 번째 실패 또는 이미 잠긴 계좌는 APW0101을 반환한다")
    void rejectsLockedPassword() {
        given(processor.verify(1L, 101L, "9999")).willReturn(new AccountPasswordAttemptResult(101L, false, 5, 0, true));

        assertThatThrownBy(() -> service.verify(command("9999")))
                .isInstanceOfSatisfying(AccountPasswordVerificationFailedException.class, exception -> assertThat(
                                exception.getErrorCode())
                        .isEqualTo(AccountPasswordErrorCode.PASSWORD_LOCKED));
        verify(storePort, never())
                .save(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("계좌비밀번호가 숫자 4자리가 아니면 CMN0001을 반환한다")
    void rejectsInvalidPasswordFormat() {
        assertThatThrownBy(() -> service.verify(command("12a")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_INPUT));
        verify(processor, never())
                .verify(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    private VerifyAccountPasswordCommand command(String password) {
        return new VerifyAccountPasswordCommand(1L, 101L, password);
    }
}
