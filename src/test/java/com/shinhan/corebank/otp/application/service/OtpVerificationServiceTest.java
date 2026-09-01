package com.shinhan.corebank.otp.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.otp.application.port.in.VerifyOtpCommand;
import com.shinhan.corebank.otp.domain.exception.OtpVerificationFailedException;
import com.shinhan.corebank.otp.domain.model.OtpAttemptResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 커밋된 OTP 처리 결과가 성공 응답 또는 횟수 포함 예외로 변환되는지 검증한다.
@ExtendWith(MockitoExtension.class)
class OtpVerificationServiceTest {

    @Mock
    OtpVerificationProcessor processor;

    @Test
    @DisplayName("다섯 번째 오답 처리 결과를 OTP0103과 5/0 예외로 변환한다")
    void convertsFifthFailureToLockedException() {
        VerifyOtpCommand command = new VerifyOtpCommand(1L, "OTP_REQ_test", "000000");
        when(processor.process(command))
                .thenReturn(OtpVerificationProcessResult.failure(new OtpAttemptResult(5, 0, true)));

        assertThatThrownBy(() -> new OtpVerificationService(processor).verify(command))
                .isInstanceOfSatisfying(OtpVerificationFailedException.class, exception -> {
                    assertThat(exception.getErrorCode().getCode()).isEqualTo("OTP0103");
                    assertThat(exception.getAttemptResult().remainingAttempts()).isZero();
                });
    }

    @Test
    @DisplayName("검증 성공 처리 결과의 otpAuthToken을 그대로 반환한다")
    void returnsAuthToken() {
        VerifyOtpCommand command = new VerifyOtpCommand(1L, "OTP_REQ_test", "123456");
        when(processor.process(command)).thenReturn(OtpVerificationProcessResult.success("OTP_AUTH_test"));

        assertThat(new OtpVerificationService(processor).verify(command).otpAuthToken())
                .isEqualTo("OTP_AUTH_test");
    }
}
