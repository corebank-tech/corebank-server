package com.shinhan.corebank.otp.application.service;

import com.shinhan.corebank.otp.application.port.in.IssueOtpCommand;
import com.shinhan.corebank.otp.application.port.in.IssueOtpResult;
import com.shinhan.corebank.otp.application.port.out.OtpCodeGeneratorPort;
import com.shinhan.corebank.otp.application.port.out.OtpCodeHashPort;
import com.shinhan.corebank.otp.application.port.out.OtpTokenGeneratorPort;
import com.shinhan.corebank.otp.application.port.out.OtpTransactionDataCanonicalizerPort;
import com.shinhan.corebank.otp.application.port.out.OtpVerificationRequestPort;
import com.shinhan.corebank.otp.config.OtpProperties;
import com.shinhan.corebank.otp.domain.model.OtpVerificationRequest;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 잠금을 획득한 고객의 기존 OTP 만료와 신규 OTP 저장을 한 트랜잭션으로 처리한다.
@Service
@RequiredArgsConstructor
public class OtpIssueProcessor {

    private final OtpVerificationRequestPort requestPort;
    private final OtpCodeGeneratorPort codeGeneratorPort;
    private final OtpTokenGeneratorPort tokenGeneratorPort;
    private final OtpCodeHashPort codeHashPort;
    private final OtpTransactionDataCanonicalizerPort canonicalizerPort;
    private final OtpProperties properties;
    private final Clock clock;

    @Transactional
    public IssueOtpResult issue(IssueOtpCommand command) {
        LocalDateTime now = LocalDateTime.now(clock);
        // 한 고객에게 유효한 OTP가 하나만 남도록 기존 활성 요청을 먼저 만료한다.
        requestPort.expireActiveRequests(command.customerId(), now);

        String otpRequestId = tokenGeneratorPort.generateRequestId();
        String otpCode = codeGeneratorPort.generate();
        String canonicalTransactionData = canonicalizerPort.canonicalize(command.transactionData());
        requestPort.save(OtpVerificationRequest.issue(
                otpRequestId,
                command.customerId(),
                command.transactionType(),
                canonicalTransactionData,
                codeHashPort.hash(otpCode),
                now.plus(properties.codeTtl()),
                now));

        // Phase 1 Mock에서는 실제 전송 대신 발급 번호를 응답에 포함한다.
        return new IssueOtpResult(otpRequestId, otpCode, properties.codeTtl().toSeconds());
    }
}
