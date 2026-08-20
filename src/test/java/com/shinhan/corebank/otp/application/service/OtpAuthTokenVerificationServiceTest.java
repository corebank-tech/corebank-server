package com.shinhan.corebank.otp.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerification;
import com.shinhan.corebank.otp.api.OtpTransactionType;
import com.shinhan.corebank.otp.application.port.out.OtpAuthTokenStorePort;
import com.shinhan.corebank.otp.application.port.out.OtpTransactionDataCanonicalizerPort;
import com.shinhan.corebank.otp.application.port.out.OtpVerificationRequestPort;
import com.shinhan.corebank.otp.domain.model.OtpVerificationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 인증한 거래내용이 다를 때 정상 otpAuthToken을 소진하지 않는지 검증한다.
@ExtendWith(MockitoExtension.class)
class OtpAuthTokenVerificationServiceTest {

    @Mock OtpAuthTokenStorePort tokenStorePort;
    @Mock OtpVerificationRequestPort requestPort;
    @Mock OtpTransactionDataCanonicalizerPort canonicalizerPort;

    OtpAuthTokenVerificationService service;

    @BeforeEach
    void setUp() {
        service = new OtpAuthTokenVerificationService(
                tokenStorePort,
                requestPort,
                canonicalizerPort,
                new OtpTransactionDataValidator()
        );
    }

    @Test
    @DisplayName("거래내용이 다르면 OTP0102를 반환하고 토큰을 유지한다")
    void transactionMismatchDoesNotConsume() {
        Map<String, Object> data = Map.of("amount", 200_000L);
        when(tokenStorePort.findRequestId("OTP_AUTH_test"))
                .thenReturn(Optional.of("OTP_REQ_test"));
        when(requestPort.findVerifiedById("OTP_REQ_test"))
                .thenReturn(Optional.of(verifiedRequest()));
        when(canonicalizerPort.canonicalize(data)).thenReturn("{\"amount\":200000}");

        assertThatThrownBy(() -> service.verifyAndConsume(
                new OtpAuthTokenVerification(
                        "OTP_AUTH_test",
                        1L,
                        OtpTransactionType.IMMEDIATE_TRANSFER,
                        data
                )
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                org.assertj.core.api.Assertions.assertThat(exception.getErrorCode().getCode())
                        .isEqualTo("OTP0102")
        );

        verify(tokenStorePort, never()).consumeIfMatches(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    private OtpVerificationRequest verifiedRequest() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 20, 10, 0);
        return new OtpVerificationRequest(
                "OTP_REQ_test",
                1L,
                OtpTransactionType.IMMEDIATE_TRANSFER,
                "{\"amount\":100000}",
                "hash",
                0,
                false,
                true,
                now,
                now.plusMinutes(3),
                now.minusMinutes(1)
        );
    }
}
