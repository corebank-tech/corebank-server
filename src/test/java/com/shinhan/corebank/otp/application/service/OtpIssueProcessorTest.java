package com.shinhan.corebank.otp.application.service;

import com.shinhan.corebank.otp.api.OtpTransactionType;
import com.shinhan.corebank.otp.application.port.in.IssueOtpCommand;
import com.shinhan.corebank.otp.application.port.in.IssueOtpResult;
import com.shinhan.corebank.otp.application.port.out.OtpCodeGeneratorPort;
import com.shinhan.corebank.otp.application.port.out.OtpCodeHashPort;
import com.shinhan.corebank.otp.application.port.out.OtpTokenGeneratorPort;
import com.shinhan.corebank.otp.application.port.out.OtpTransactionDataCanonicalizerPort;
import com.shinhan.corebank.otp.application.port.out.OtpVerificationRequestPort;
import com.shinhan.corebank.otp.config.OtpProperties;
import com.shinhan.corebank.otp.domain.model.OtpVerificationRequest;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// OTP 발급 트랜잭션의 기존 요청 만료와 신규 요청 저장 순서를 검증한다.
@ExtendWith(MockitoExtension.class)
class OtpIssueProcessorTest {

    @Mock OtpVerificationRequestPort requestPort;
    @Mock OtpCodeGeneratorPort codeGeneratorPort;
    @Mock OtpTokenGeneratorPort tokenGeneratorPort;
    @Mock OtpCodeHashPort codeHashPort;
    @Mock OtpTransactionDataCanonicalizerPort canonicalizerPort;

    @Test
    @DisplayName("기존 활성 OTP를 만료한 뒤 180초짜리 신규 OTP를 저장한다")
    void expiresExistingAndIssuesNewOtp() {
        OtpIssueProcessor processor = new OtpIssueProcessor(
                requestPort,
                codeGeneratorPort,
                tokenGeneratorPort,
                codeHashPort,
                canonicalizerPort,
                new OtpProperties(
                        Duration.ofMinutes(3),
                        Duration.ofMinutes(5),
                        true
                ),
                Clock.fixed(Instant.parse("2026-08-20T01:00:00Z"), ZoneOffset.UTC)
        );
        Map<String, Object> data = Map.of("amount", 100_000L);
        when(tokenGeneratorPort.generateRequestId()).thenReturn("OTP_REQ_test");
        when(codeGeneratorPort.generate()).thenReturn("012345");
        when(codeHashPort.hash("012345")).thenReturn("bcrypt-hash");
        when(canonicalizerPort.canonicalize(data)).thenReturn("{\"amount\":100000}");

        IssueOtpResult result = processor.issue(new IssueOtpCommand(
                1L,
                OtpTransactionType.IMMEDIATE_TRANSFER,
                data
        ));

        var ordered = inOrder(requestPort);
        ordered.verify(requestPort).expireActiveRequests(eq(1L), any());
        ArgumentCaptor<OtpVerificationRequest> captor =
                ArgumentCaptor.forClass(OtpVerificationRequest.class);
        verify(requestPort).save(captor.capture());
        assertThat(captor.getValue().codeHash()).isEqualTo("bcrypt-hash");
        assertThat(result).isEqualTo(new IssueOtpResult("OTP_REQ_test", "012345", 180));
    }
}
