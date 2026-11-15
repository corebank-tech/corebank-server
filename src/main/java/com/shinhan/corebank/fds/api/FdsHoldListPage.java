package com.shinhan.corebank.fds.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/** 실제 관리자 FDS 보류 목록의 페이지 응답이다. */
public record FdsHoldListPage(List<FdsHoldListItem> items, int page, int size, long totalElements, int totalPages) {
    @Schema(name = "FdsHoldListItem", description = "관리자 FDS 보류 거래 목록 항목")
    public record FdsHoldListItem(
            long transferHoldId,
            LocalDateTime heldAt,
            long amount,
            String recipientBankCode,
            String recipientAccountMasked,
            String policyCode,
            String holdStatus,
            String investigationStatus,
            String behaviorScoreStatus,
            String riskAccountStatus,
            Long riskRecordId,
            String datasetVersion,
            String latestCallResult,
            String latestActionStatus,
            String availablePath) {}
}
