package com.shinhan.corebank.customer.adapter.in.web;

import com.shinhan.corebank.customer.application.port.in.CustomerInfoResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

// 마스킹된 고객 기본정보를 HTTP 응답으로 반환한다.
public record CustomerInfoResponse(
        @Schema(description = "내부 고객 식별자", example = "1")
        Long customerId,

        @Schema(description = "마스킹된 고객명", example = "홍*동")
        String userName,

        @Schema(description = "로그인 아이디", example = "corebank01")
        String userId,

        @Schema(description = "생년월일", example = "1990-01-15")
        String birthDate,

        @Schema(description = "마스킹된 휴대폰 번호", example = "010****5678")
        String phoneNumber,

        @Schema(description = "마스킹된 이메일", example = "user****@mail.com")
        String email,

        @Schema(description = "가입 일시", example = "2026-08-01T10:00:00+09:00")
        OffsetDateTime joinedAt
) {

    // application 조회 결과를 HTTP 응답 DTO로 변환한다.
    public static CustomerInfoResponse from(CustomerInfoResult result) {
        return new CustomerInfoResponse(
                result.customerId(),
                result.userName(),
                result.userId(),
                result.birthDate(),
                result.phoneNumber(),
                result.email(),
                result.joinedAt()
        );
    }
}
