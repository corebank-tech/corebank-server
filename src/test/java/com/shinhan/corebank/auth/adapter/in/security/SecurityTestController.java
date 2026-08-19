package com.shinhan.corebank.auth.adapter.in.security;

import com.shinhan.corebank.common.response.ApiResponse;
import java.util.Map;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** SecurityConfig의 경로별 접근 정책만 검증하기 위한 테스트 전용 컨트롤러 */
@TestComponent
@RestController
class SecurityTestController {

    @PostMapping("/auth/login")
    ApiResponse<Void> login() {
        return ApiResponse.success();
    }

    @PostMapping({
            "/auth/terms/check",
            "/auth/verify-account",
            "/auth/check-id",
            "/auth/signup/validate",
            "/auth/signup/complete",
            "/auth/email-verifications",
            "/auth/email-verifications/{emailVerificationId}/verify"
    })
    ApiResponse<Void> signup() {
        return ApiResponse.success();
    }

    @GetMapping("/products/test")
    ApiResponse<String> publicProduct() {
        return ApiResponse.success("product");
    }

    @GetMapping("/customers/me")
    ApiResponse<String> protectedCustomer() {
        return ApiResponse.success("customer");
    }

    @PostMapping("/customers/me")
    ApiResponse<String> updateCustomer() {
        return ApiResponse.success("updated");
    }

    @GetMapping("/actuator/health")
    Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @GetMapping("/actuator/info")
    Map<String, String> actuatorInfo() {
        return Map.of("name", "corebank");
    }
}
