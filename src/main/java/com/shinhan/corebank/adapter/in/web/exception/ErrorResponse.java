package com.shinhan.corebank.adapter.in.web.exception;

import com.shinhan.corebank.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

// 실패 응답 전용 봉투. 성공 응답은 ApiResponse 가 전담한다
public record ErrorResponse(String code, String message, Object data) {

    public static ResponseEntity<ErrorResponse> toResponseEntity(ErrorCode errorCode) {
        return toResponseEntity(errorCode, errorCode.getMessage());
    }

    public static ResponseEntity<ErrorResponse> toResponseEntity(ErrorCode errorCode, String message) {
        return ResponseEntity.status(HttpStatus.valueOf(errorCode.getStatus()))
                .body(new ErrorResponse(errorCode.getCode(), message, null));
    }
}
