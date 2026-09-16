package com.shinhan.corebank.otp.domain.exception;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.otp.domain.model.OtpAttemptResult;

// OTP 오답과 잠금 응답에 오류 횟수 데이터를 함께 전달한다.
public class OtpVerificationFailedException extends BusinessException {

    private final OtpAttemptResult attemptResult;

    public OtpVerificationFailedException(OtpAttemptResult attemptResult) {
        super(attemptResult.locked() ? OtpErrorCode.ATTEMPTS_EXCEEDED : OtpErrorCode.OTP_CODE_MISMATCH);
        this.attemptResult = attemptResult;
    }

    public OtpAttemptResult getAttemptResult() {
        return attemptResult;
    }
}
