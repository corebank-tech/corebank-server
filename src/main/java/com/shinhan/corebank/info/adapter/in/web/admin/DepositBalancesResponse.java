package com.shinhan.corebank.info.adapter.in.web.admin;

import com.shinhan.corebank.info.domain.DepositBalanceTrend;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

// 필드명은 info_mart.md §5-2 를 따른다. 바꾸려면 FE(ADM-03)와 먼저 맞춘다.
public record DepositBalancesResponse(
        @Schema(description = "적재가 빠짐없이 끝난 마지막 기준일. 적재 이력이 없으면 null", example = "2026-10-28", nullable = true)
                LocalDate asOf,
        @Schema(description = "asOf 이하의 COMPLETED 영업일 잔액. 휴일은 없다") List<DepositBalanceItem> items) {

    public record DepositBalanceItem(
            @Schema(description = "기준일", example = "2026-10-01") LocalDate baseDate,
            @Schema(description = "그날 총 수신 잔액(원)", example = "1523400000000") long totalBalance) {

        static DepositBalanceItem from(DepositBalanceTrend.Point point) {
            return new DepositBalanceItem(point.baseDate(), point.totalBalance());
        }
    }

    static DepositBalancesResponse from(DepositBalanceTrend trend) {
        return new DepositBalancesResponse(
                trend.asOf(),
                trend.items().stream().map(DepositBalanceItem::from).toList());
    }
}
