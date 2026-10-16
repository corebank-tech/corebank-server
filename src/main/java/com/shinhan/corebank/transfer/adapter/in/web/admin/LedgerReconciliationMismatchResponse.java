package com.shinhan.corebank.transfer.adapter.in.web.admin;

import com.shinhan.corebank.transfer.application.port.in.LedgerReconciliationMismatchQueryUseCase.LedgerReconciliationMismatchView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record LedgerReconciliationMismatchResponse(
        @Schema(description = "대사한 COB의 마감 대상 영업일", example = "2026-10-13") LocalDate businessDate,
        @Schema(description = "불일치 계좌 내부 식별자", example = "1001") Long accountId,
        @Schema(description = "원장 누적 합계(입금 - 출금). 원장이 없는 계좌는 0", example = "1000000") long ledgerBalance,
        @Schema(description = "대사 시점 계좌 잔액", example = "900000") long accountBalance,
        @Schema(description = "원장 - 잔액. 양수면 잔액이 원장보다 적다", example = "100000") long difference,
        @Schema(description = "대사가 불일치를 확인한 시각", example = "2026-10-13T23:31:05+09:00") OffsetDateTime detectedAt) {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    static LedgerReconciliationMismatchResponse from(LocalDate businessDate, LedgerReconciliationMismatchView view) {
        return new LedgerReconciliationMismatchResponse(
                businessDate,
                view.accountId(),
                view.ledgerBalance(),
                view.accountBalance(),
                view.difference(),
                view.detectedAt().atZone(KOREA_ZONE).toOffsetDateTime());
    }
}
