package com.shinhan.corebank.signup.adapter.in.web;

import com.shinhan.corebank.adapter.in.web.exception.ErrorResponse;
import com.shinhan.corebank.common.exception.ErrorCode;
import com.shinhan.corebank.signup.adapter.in.web.dto.AccountVerificationFailureData;
import com.shinhan.corebank.signup.domain.exception.AccountVerificationFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// 계좌 인증 실패를 선택적인 실패 횟수 data가 있는 응답으로 변환한다.
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class SignupAccountVerificationExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(SignupAccountVerificationExceptionHandler.class);

    @ExceptionHandler(AccountVerificationFailedException.class)
    public ResponseEntity<ErrorResponse> handle(AccountVerificationFailedException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        AccountVerificationFailureData data = exception
                .getAttemptResult()
                .map(AccountVerificationFailureData::from)
                .orElse(null);

        log.warn("[{}] {}", errorCode.getCode(), exception.getMessage());

        return ResponseEntity.status(HttpStatus.valueOf(errorCode.getStatus()))
                .body(new ErrorResponse(errorCode.getCode(), exception.getMessage(), data));
    }
}
