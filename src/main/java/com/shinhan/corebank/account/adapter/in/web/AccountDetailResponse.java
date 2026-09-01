package com.shinhan.corebank.account.adapter.in.web;

import com.shinhan.corebank.account.application.port.in.AccountDetailResult;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.common.util.MaskingUtil;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.OffsetDateTime;

// 계좌상세 정보와 마스킹한 예금주명을 API 응답으로 변환한다.
public record AccountDetailResponse(
        @Schema(description = "조회 기준 일시", example = "2026-07-30T15:50:00+09:00") OffsetDateTime asOf,
        @Schema(description = "계좌 내부 식별자", example = "101") Long accountId,
        @Schema(description = "화면에 표시할 계좌명", example = "생활비 통장") String accountName,
        @Schema(description = "일부 마스킹한 예금주명", example = "홍*동") String ownerName,
        @Schema(description = "하이픈 없는 계좌번호", example = "110550051877") String accountNumber,
        @Schema(description = "현재 잔액(원)", example = "1500000") long balance,
        @Schema(description = "출금 가능 잔액(원). 출금 제한 시 0", example = "1500000") long availableBalance,
        @Schema(description = "계좌 개설일", example = "2025-03-10") LocalDate openedDate,
        @Schema(description = "계좌 상태", example = "ACTIVE") AccountStatus status,
        @Schema(description = "계좌비밀번호 연속 오류 횟수", example = "2") int passwordFailureCount,
        @Schema(description = "계좌비밀번호 잠금 여부", example = "false") boolean passwordLocked) {

    // application 결과에 세션 예금주명을 마스킹하여 응답을 완성한다.
    public static AccountDetailResponse from(AccountDetailResult result, String ownerName) {
        return new AccountDetailResponse(
                result.asOf(),
                result.accountId(),
                result.accountName(),
                MaskingUtil.maskName(ownerName),
                result.accountNumber(),
                result.balance(),
                result.availableBalance(),
                result.openedDate(),
                result.status(),
                result.passwordFailureCount(),
                result.passwordLocked());
    }
}
