package com.shinhan.corebank.auth.adapter.in.web;

import com.shinhan.corebank.adapter.in.web.exception.ErrorResponse;
import com.shinhan.corebank.auth.domain.exception.LoginFailedException;
import com.shinhan.corebank.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// 로그인 실패 유형에 따라 선택적인 시도 횟수 데이터를 응답으로 변환
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class LoginExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(LoginExceptionHandler.class);

    @ExceptionHandler(LoginFailedException.class)
    public ResponseEntity<ErrorResponse> handleLoginFailure(
            LoginFailedException exception
    ) {
        ErrorCode errorCode = exception.getErrorCode();

        LoginFailureData data = exception.getAttemptResult()
                .map(LoginFailureData::from)
                .orElse(null);

        log.warn("[{}] {}", errorCode.getCode(), exception.getMessage());

        return ResponseEntity
                .status(HttpStatus.valueOf(errorCode.getStatus()))
                .body(new ErrorResponse(
                        errorCode.getCode(),
                        exception.getMessage(),
                        data
                ));
    }
}
