package com.shinhan.corebank.account.adapter.in.web;

import com.shinhan.corebank.account.application.port.in.AccountGroupCode;
import com.shinhan.corebank.account.application.port.in.AccountOverviewResult;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

public record AccountOverviewResponse(

        @Schema(
                description = "조회 기준 시각",
                example = "2026-08-20T11:00:00+09:00"
        )
        OffsetDateTime asOf,

        @Schema(
                description = "보유 계좌 총 자산 금액(원)",
                example = "12500000"
        )
        long totalAssets,

        @Schema(
                description = "계좌 유형별 그룹 목록"
        )
        List<GroupResponse> items
) {

    public static AccountOverviewResponse from(
            AccountOverviewResult result
    ) {
        return new AccountOverviewResponse(
                result.asOf(),
                result.totalAssets(),
                result.items().stream()
                        .map(GroupResponse::from)
                        .toList()
        );
    }

    public record GroupResponse(

            @Schema(
                    description = "계좌 그룹 코드",
                    example = "DEMAND_DEPOSIT"
            )
            AccountGroupCode groupCode,

            @Schema(
                    description = "계좌 그룹 표시명",
                    example = "입출금"
            )
            String groupName,

            @Schema(
                    description = "해당 그룹 계좌 잔액 합계(원)",
                    example = "3500000"
            )
            long groupTotalBalance,

            @Schema(
                    description = "그룹에 포함된 계좌 목록"
            )
            List<AccountItemResponse> accounts
    ) {

        private static GroupResponse from(
                AccountOverviewResult.Group group
        ) {
            return new GroupResponse(
                    group.groupCode(),
                    group.groupName(),
                    group.groupTotalBalance(),
                    group.accounts().stream()
                            .map(AccountItemResponse::from)
                            .toList()
            );
        }
    }

    public record AccountItemResponse(
            @Schema(description = "계좌 내부 식별자", example = "101")
            Long accountId,

            @Schema(description = "화면에 표시할 계좌명", example = "생활비통장")
            String accountName,

            @Schema(description = "계좌번호", example = "088010000001")
            String accountNumber,

            @Schema(
                    description = "계좌 유형",
                    example = "DEMAND_DEPOSIT"
            )
            AccountType accountType,

            @Schema(description = "계좌 잔액(원)", example = "2500000")
            long balance,

            @Schema(
                    description = "현재 출금가능금액(원). 출금 제한 상태이면 0",
                    example = "2500000"
            )
            long availableBalance,

            @Schema(description = "계좌 상태", example = "ACTIVE")
            AccountStatus status,

            @Schema(
                    description = "계좌 개설일",
                    example = "2026-07-30"
            )
            LocalDate openedDate,

            @Schema(
                    description = "마지막 거래 일시. 거래내역이 없으면 null",
                    example = "2026-08-19T14:30:00",
                    nullable = true
            )
            LocalDateTime lastTransactionAt,

            @Schema(
                    description = "만기일. 입출금계좌는 null",
                    example = "2027-08-20",
                    nullable = true
            )
            LocalDate maturityDate,

            @Schema(
                    description = "이체 가능 여부",
                    example = "true"
            )
            boolean transferEnabled
    ) {

        private static AccountItemResponse from(
                AccountOverviewResult.AccountItem account
        ) {
            return new AccountItemResponse(
                    account.accountId(),
                    account.accountName(),
                    account.accountNumber(),
                    account.accountType(),
                    account.balance(),
                    account.availableBalance(),
                    account.status(),
                    account.openedDate(),
                    account.lastTransactionAt(),
                    account.maturityDate(),
                    account.transferEnabled()
            );
        }
    }
}