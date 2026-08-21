package com.shinhan.corebank.otp.adapter.in.web;

import com.shinhan.corebank.adapter.in.web.exception.ErrorResponse;
import com.shinhan.corebank.common.exception.ErrorCode;
import com.shinhan.corebank.otp.domain.exception.OtpVerificationFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// OTP 오답과 잠금 오류에 실패 횟수 및 잔여 횟수를 포함한다.
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class OtpExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(OtpExceptionHandler.class);

    @ExceptionHandler(OtpVerificationFailedException.class)
    public ResponseEntity<ErrorResponse> handleVerificationFailure(
            OtpVerificationFailedException exception
    ) {
        ErrorCode errorCode = exception.getErrorCode();
        OtpFailureData data = new OtpFailureData(
                null,
                exception.getAttemptResult().errorCount(),
                exception.getAttemptResult().remainingAttempts()
        );

        log.warn("[{}] {}", errorCode.getCode(), errorCode.getMessage());

        return ResponseEntity
                .status(HttpStatus.valueOf(errorCode.getStatus()))
                .body(new ErrorResponse(
                        errorCode.getCode(),
                        errorCode.getMessage(),
                        data
                ));
    }
}
