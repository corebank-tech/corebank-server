package com.shinhan.corebank.scheduledtransfer.adapter.in.web;

import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferExecutionResultItem;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferExecutionResultPage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

public record ScheduledTransferExecutionResultPageResponse(
        @Schema(description = "조회 기간 내 정상/오류/취소 건수·금액 집계") ScheduledTransferExecutionResultSummaryResponse summary,
        @Schema(description = "현재 페이지 번호(0부터 시작)") int page,
        @Schema(description = "페이지 크기") int size,
        @Schema(description = "전체 건수") long totalCount,
        @Schema(description = "전체 페이지 수") int totalPages,
        @Schema(description = "처리결과 목록") List<ScheduledTransferExecutionResultItemResponse> items) {
    public static ScheduledTransferExecutionResultPageResponse from(ScheduledTransferExecutionResultPage result) {
        Page<ScheduledTransferExecutionResultItem> page = result.page();
        return new ScheduledTransferExecutionResultPageResponse(
                ScheduledTransferExecutionResultSummaryResponse.from(result.summary()),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getContent().stream()
                        .map(ScheduledTransferExecutionResultItemResponse::from)
                        .toList());
    }
}
