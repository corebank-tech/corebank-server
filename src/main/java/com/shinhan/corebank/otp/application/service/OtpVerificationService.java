package com.shinhan.corebank.otp.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.otp.application.port.in.VerifyOtpCommand;
import com.shinhan.corebank.otp.application.port.in.VerifyOtpResult;
import com.shinhan.corebank.otp.application.port.in.VerifyOtpUseCase;
import com.shinhan.corebank.otp.domain.exception.OtpVerificationFailedException;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

// OTP 입력 형식을 확인하고 커밋된 검증 결과를 API 성공 또는 업무 예외로 변환한다.
@Service
public class OtpVerificationService implements VerifyOtpUseCase {

    private static final Pattern OTP_PATTERN = Pattern.compile("^\\d{6}$");
    private final OtpVerificationProcessor processor;

    public OtpVerificationService(OtpVerificationProcessor processor) {
        this.processor = processor;
    }

    @Override
    public VerifyOtpResult verify(VerifyOtpCommand command) {
        if (command == null
                || command.customerId() == null
                || command.otpRequestId() == null
                || command.otpRequestId().isBlank()
                || command.otpCode() == null
                || !OTP_PATTERN.matcher(command.otpCode()).matches()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        OtpVerificationProcessResult result = processor.process(command);
        if (!result.success()) {
            // 처리 트랜잭션 커밋 후 예외를 던져 error_count 갱신 롤백을 방지한다.
            throw new OtpVerificationFailedException(result.attemptResult());
        }
        return new VerifyOtpResult(result.otpAuthToken());
    }
}
