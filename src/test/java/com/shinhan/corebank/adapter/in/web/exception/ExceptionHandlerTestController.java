package com.shinhan.corebank.adapter.in.web.exception;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ApiExceptionHandlerTest 전용. 각 예외 상황을 유발하기 위한 더미 엔드포인트만 제공한다.
 */
@RestController
public class ExceptionHandlerTestController {

    @GetMapping("/test/errors/business")
    public ApiResponse<Void> business() {
        throw new BusinessException(CommonErrorCode.INVALID_DATE_RANGE);
    }

    @PostMapping("/test/errors/validate")
    public ApiResponse<Void> validate(@Valid @RequestBody TestRequest request) {
        return ApiResponse.success();
    }

    @GetMapping("/test/errors/type-mismatch")
    public ApiResponse<Void> typeMismatch(@RequestParam Long id) {
        return ApiResponse.success();
    }

    @GetMapping("/test/errors/missing-param")
    public ApiResponse<Void> missingParam(@RequestParam String required) {
        return ApiResponse.success();
    }

    @GetMapping("/test/errors/missing-header")
    public ApiResponse<Void> missingHeader(@RequestHeader("Idempotency-Key") String key) {
        return ApiResponse.success();
    }

    @GetMapping("/test/errors/unexpected")
    public ApiResponse<Void> unexpected() {
        throw new IllegalStateException("boom");
    }

    @GetMapping("/test/errors/optimistic-lock") public ApiResponse<Void> optimisticLock() {
        throw new OptimisticLockingFailureException(
                "동시 수정 충돌"
        );
    }

    @GetMapping("/test/errors/ok")
    public ApiResponse<String> ok() {
        return ApiResponse.success("hello");
    }

    record TestRequest(@NotBlank String name) {
    }
}
