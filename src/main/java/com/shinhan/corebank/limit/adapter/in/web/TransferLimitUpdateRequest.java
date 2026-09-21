package com.shinhan.corebank.limit.adapter.in.web;

import com.shinhan.corebank.limit.application.port.in.dto.TransferLimitCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 이체한도 변경 요청(REQ-TRSF-025). 1회·1일 한도를 함께 교체한다.
 *
 * <p>accountPasswordAuthToken과 otpAuthToken을 모두 소비해 2단계 인증을 완료한다.
 *
 * <p>정책 상한(POL-015·016) 검사는 여기서 끝낸다. 위반 시 CMN0001 로 응답한다 - 이 둘은 단일
 * 필드 제약이고, api_conventions.md §4-1 이 도메인 오류코드 신설을 제한한다. 두 값의 관계인
 * "1회 ≤ 1일"만 도메인이 검사해 LMT0004 를 던진다.
 */
public record TransferLimitUpdateRequest(
        @Schema(description = "1회 이체한도. 최대 5,000만원", example = "3000000") @NotNull @Positive @Max(50_000_000L)
                Long oneTimeLimit,
        @Schema(description = "1일 이체한도. 최대 1억원", example = "10000000") @NotNull @Positive @Max(100_000_000L)
                Long dailyLimit,
        @Schema(description = "계좌비밀번호 검증으로 발급된 일회용 인증 토큰", example = "ACC_PWD_9aB3cF8dE2xY7zL1kM0pN4qR5sT6uV") @NotBlank
                String accountPasswordAuthToken,
        @Schema(description = "OTP 검증으로 발급된 인증 토큰", example = "OTP_AUTH_7xP9qK2RmY5vLw8ZbC6dE4fG1hH0jM3n") @NotBlank
                String otpAuthToken) {

    public TransferLimitCommand toCommand() {
        return TransferLimitCommand.builder()
                .oneTimeLimit(oneTimeLimit)
                .dailyLimit(dailyLimit)
                .accountPasswordAuthToken(accountPasswordAuthToken)
                .otpAuthToken(otpAuthToken)
                .build();
    }
}
