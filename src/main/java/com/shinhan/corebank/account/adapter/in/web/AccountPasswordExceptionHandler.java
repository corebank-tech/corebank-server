package com.shinhan.corebank.account.adapter.in.web;

import com.shinhan.corebank.account.domain.exception.AccountPasswordVerificationFailedException;
import com.shinhan.corebank.adapter.in.web.exception.ErrorResponse;
import com.shinhan.corebank.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// 비밀번호 불일치와 잠금 오류에 최신 시도 횟수를 포함한다.
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class AccountPasswordExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(
            AccountPasswordExceptionHandler.class
    );

    @ExceptionHandler(
            AccountPasswordVerificationFailedException.class
    )
    public ResponseEntity<ErrorResponse> handleVerificationFailure(
            AccountPasswordVerificationFailedException exception
    ) {
        ErrorCode errorCode = exception.getErrorCode();

        log.warn("[{}] {}", errorCode.getCode(), errorCode.getMessage());

        return ResponseEntity
                .status(HttpStatus.valueOf(errorCode.getStatus()))
                .body(new ErrorResponse(
                        errorCode.getCode(),
                        errorCode.getMessage(),
                        AccountPasswordFailureData.from(
                                exception.getAttemptResult()
                        )
                ));
    }
}
