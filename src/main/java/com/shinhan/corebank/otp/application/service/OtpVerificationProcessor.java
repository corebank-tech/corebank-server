package com.shinhan.corebank.otp.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.otp.application.port.in.VerifyOtpCommand;
import com.shinhan.corebank.otp.application.port.out.OtpAuthTokenStorePort;
import com.shinhan.corebank.otp.application.port.out.OtpCodeHashPort;
import com.shinhan.corebank.otp.application.port.out.OtpTokenGeneratorPort;
import com.shinhan.corebank.otp.application.port.out.OtpVerificationRequestPort;
import com.shinhan.corebank.otp.config.OtpProperties;
import com.shinhan.corebank.otp.domain.exception.OtpErrorCode;
import com.shinhan.corebank.otp.domain.model.OtpAttemptResult;
import com.shinhan.corebank.otp.domain.model.OtpAuthTokenPayload;
import com.shinhan.corebank.otp.domain.model.OtpVerificationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

// OTP 행 잠금부터 실패 횟수 또는 성공 토큰 저장까지 하나의 처리 트랜잭션으로 수행한다.
@Service
@RequiredArgsConstructor
public class OtpVerificationProcessor {

    private final OtpVerificationRequestPort requestPort;
    private final OtpCodeHashPort codeHashPort;
    private final OtpTokenGeneratorPort tokenGeneratorPort;
    private final OtpAuthTokenStorePort authTokenStorePort;
    private final OtpProperties properties;
    private final Clock clock;

    @Transactional
    public OtpVerificationProcessResult process(VerifyOtpCommand command) {
        OtpVerificationRequest request = requestPort.findByIdForUpdate(command.otpRequestId())
                .orElseThrow(() -> new BusinessException(OtpErrorCode.OTP_REQUEST_NOT_FOUND));

        if (!request.belongsTo(command.customerId())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        if (request.used()) {
            throw new BusinessException(OtpErrorCode.OTP_REQUEST_NOT_FOUND);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (request.expiredAt(now)) {
            throw new BusinessException(OtpErrorCode.OTP_EXPIRED);
        }
        if (request.locked()) {
            return OtpVerificationProcessResult.failure(request.currentAttemptResult());
        }
        if (!codeHashPort.matches(command.otpCode(), request.codeHash())) {
            OtpAttemptResult attemptResult = request.recordFailure();
            requestPort.save(request);
            return OtpVerificationProcessResult.failure(attemptResult);
        }

        request.verify(now);
        requestPort.save(request);

        String otpAuthToken = tokenGeneratorPort.generateAuthToken();
        // Redis 저장 실패 시 검증 완료 DB 변경도 함께 롤백되도록 처리 트랜잭션 안에서 저장한다.
        authTokenStorePort.save(
                otpAuthToken,
                new OtpAuthTokenPayload(
                        request.verificationRequestId(),
                        request.customerId()
                ),
                properties.authTokenTtl()
        );
        return OtpVerificationProcessResult.success(otpAuthToken);
    }
}
