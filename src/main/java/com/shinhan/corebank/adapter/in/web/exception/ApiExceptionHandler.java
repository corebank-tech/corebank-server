package com.shinhan.corebank.adapter.in.web.exception;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

// 모든 REST 컨트롤러의 예외를 가로채 {code, message, data} 로 변환. 각 파트는 수정할 필요 없음
@RestControllerAdvice(annotations = RestController.class)
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    // 각 파트가 직접 던진 예외 -> 그 Enum 의 코드·상태·메시지 그대로 반환
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        log.warn("[{}] {}", e.getErrorCode().getCode(), e.getMessage());
        return ErrorResponse.toResponseEntity(e.getErrorCode(), e.getMessage());
    }

    // @Valid 본문 검증 실패 (예: @Min(1) 인데 0) -> CMN0001
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        FieldError first = e.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = (first == null)
                ? CommonErrorCode.INVALID_INPUT.getMessage()
                : "%s: %s".formatted(first.getField(), first.getDefaultMessage());
        log.warn("[{}] {}", CommonErrorCode.INVALID_INPUT.getCode(), message);
        return ErrorResponse.toResponseEntity(CommonErrorCode.INVALID_INPUT, message);
    }

    // 파라미터 타입 불일치 (예: Long 자리에 abc, Enum 에 없는 값) -> CMN0001
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String message = "'%s' 파라미터 값이 올바르지 않습니다.".formatted(e.getName());
        log.warn("[{}] {}", CommonErrorCode.INVALID_INPUT.getCode(), message);
        return ErrorResponse.toResponseEntity(CommonErrorCode.INVALID_INPUT, message);
    }

    // 필수 쿼리 파라미터 누락 -> CMN0002
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException e) {
        String message = "필수 파라미터 '%s' 가 누락되었습니다.".formatted(e.getParameterName());
        log.warn("[{}] {}", CommonErrorCode.REQUIRED_FIELD_MISSING.getCode(), message);
        return ErrorResponse.toResponseEntity(CommonErrorCode.REQUIRED_FIELD_MISSING, message);
    }

    // 필수 헤더 누락 (예: Idempotency-Key 미전송) -> CMN0002
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException e) {
        String message = "필수 헤더 '%s' 가 누락되었습니다.".formatted(e.getHeaderName());
        log.warn("[{}] {}", CommonErrorCode.REQUIRED_FIELD_MISSING.getCode(), message);
        return ErrorResponse.toResponseEntity(CommonErrorCode.REQUIRED_FIELD_MISSING, message);
    }

    // 그 외 모든 예외 (NPE, DB 오류 등) -> CMN9999. 스택트레이스는 로그에만 남김
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외 발생", e);
        return ErrorResponse.toResponseEntity(CommonErrorCode.INTERNAL_ERROR);
    }
}
