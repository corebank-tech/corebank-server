package com.shinhan.corebank.customer.adapter.in.web.admin;

import com.shinhan.corebank.customer.application.port.in.AdminCustomerDetail;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

public record AdminCustomerDetailResponse(
        @Schema(description = "고객 내부 식별자", example = "1") Long customerId,
        @Schema(description = "로그인 아이디(앞 4자리 외 마스킹)", example = "hong*******") String userId,
        @Schema(description = "성명(마스킹)", example = "홍*동") String userName,
        @Schema(description = "생년월일(연도 외 마스킹)", example = "1990-**-**") String birthDate,
        @Schema(description = "이메일(마스킹)", example = "hong****@corebank.example.com") String email,
        @Schema(description = "휴대폰번호(가운데 4자리 마스킹)", example = "010****5678") String phoneNumber,
        @Schema(description = "연속 로그인 실패 횟수", example = "5") int loginFailureCount,
        @Schema(description = "계정 잠금 여부", example = "true") boolean accountLocked,
        @Schema(description = "최근 로그인 일시. 로그인 이력이 없으면 null", example = "2026-09-21T09:14:22+09:00")
                OffsetDateTime lastLoginAt,
        @Schema(description = "가입 완료 일시", example = "2025-08-05T10:02:00+09:00") OffsetDateTime joinedAt) {

    static AdminCustomerDetailResponse from(AdminCustomerDetail detail) {
        return new AdminCustomerDetailResponse(
                detail.customerId(),
                detail.userId(),
                detail.userName(),
                detail.birthDate(),
                detail.email(),
                detail.phoneNumber(),
                detail.loginFailureCount(),
                detail.accountLocked(),
                detail.lastLoginAt(),
                detail.joinedAt());
    }
}
