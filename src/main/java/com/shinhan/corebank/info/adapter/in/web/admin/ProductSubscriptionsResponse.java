package com.shinhan.corebank.info.adapter.in.web.admin;

import com.shinhan.corebank.info.domain.ProductSubscriptionSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

// 필드명은 info_mart.md §5-3 을 따른다. 고객 식별자와 가명 키는 응답에 없다(REQ-ADM-033).
public record ProductSubscriptionsResponse(
        @Schema(description = "적재가 빠짐없이 끝난 마지막 기준일. 적재 이력이 없으면 null", example = "2026-10-28", nullable = true)
                LocalDate asOf,
        @Schema(description = "집계한 달(yyyy-MM). 요청하지 않았고 적재 이력도 없으면 null", example = "2026-10", nullable = true)
                String month,
        @Schema(description = "그 달 전체 가입 건수. 적재 이력이 없으면 null", example = "120", nullable = true) Long subscribedCount,
        @Schema(description = "그 달 전체 해지 건수. 적재 이력이 없으면 null", example = "5", nullable = true) Long closedCount,
        @Schema(description = "상품별 가입·해지 건수") List<Item> items) {

    public record Item(
            @Schema(description = "상품 ID", example = "3") long productId,
            @Schema(description = "상품 코드", example = "DEP-12M") String productCode,
            @Schema(description = "상품명. 상품별로 가장 최근에 적재된 스냅샷", example = "쏠편한 정기예금") String productName,
            @Schema(description = "상품군", example = "DEPOSIT") String productGroup,
            @Schema(description = "가입 건수", example = "80") long subscribedCount,
            @Schema(description = "해지 건수", example = "3") long closedCount) {

        static Item from(ProductSubscriptionSummary.Line line) {
            return new Item(
                    line.productId(),
                    line.productCode(),
                    line.productName(),
                    line.productGroup(),
                    line.subscribedCount(),
                    line.closedCount());
        }
    }

    static ProductSubscriptionsResponse from(ProductSubscriptionSummary summary) {
        return new ProductSubscriptionsResponse(
                summary.asOf(),
                summary.month() == null ? null : summary.month().toString(),
                summary.subscribedCount(),
                summary.closedCount(),
                summary.items().stream().map(Item::from).toList());
    }
}
