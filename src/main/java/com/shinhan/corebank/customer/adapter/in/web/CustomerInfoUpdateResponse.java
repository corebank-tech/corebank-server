package com.shinhan.corebank.customer.adapter.in.web;

import com.shinhan.corebank.customer.application.port.in.UpdateCustomerInfoResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

// 고객정보 변경 후 마스킹된 연락처와 변경일시를 HTTP 응답으로 반환한다.
public record CustomerInfoUpdateResponse(
        @Schema(description = "내부 고객 식별자", example = "1") Long customerId,
        @Schema(description = "마스킹된 변경 휴대폰 번호", example = "010****5678") String phoneNumber,
        @Schema(description = "마스킹된 변경 이메일", example = "new-****@mail.com") String email,
        @Schema(description = "고객정보 변경 일시", example = "2026-08-22T16:30:00+09:00") OffsetDateTime updatedAt) {

    // application 변경 결과를 HTTP 응답 DTO로 변환한다.
    public static CustomerInfoUpdateResponse from(UpdateCustomerInfoResult result) {
        return new CustomerInfoUpdateResponse(
                result.customerId(), result.phoneNumber(), result.email(), result.updatedAt());
    }
}
