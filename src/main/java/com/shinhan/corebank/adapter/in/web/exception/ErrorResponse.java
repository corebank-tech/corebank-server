package com.shinhan.corebank.adapter.in.web.exception;

import com.shinhan.corebank.common.exception.ErrorCode;
import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Getter
@Builder
public class ErrorResponse {

    private final String code;
    private final String message;
    private final Object data;

    public static ResponseEntity<ErrorResponse> toResponseEntity(ErrorCode errorCode) {
        return toResponseEntity(errorCode, errorCode.getMessage());
    }

    public static ResponseEntity<ErrorResponse> toResponseEntity(ErrorCode errorCode, String message) {
        return ResponseEntity
                .status(HttpStatus.valueOf(errorCode.getStatus()))
                .body(ErrorResponse.builder()
                        .code(errorCode.getCode())
                        .message(message)
                        .data(null)
                        .build());
    }
}
