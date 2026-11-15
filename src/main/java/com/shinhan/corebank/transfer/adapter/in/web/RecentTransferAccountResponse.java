package com.shinhan.corebank.transfer.adapter.in.web;

import com.shinhan.corebank.transfer.application.port.in.RecentTransferAccountResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record RecentTransferAccountResponse(
        @Schema(description = "입금계좌번호 (하이픈 없이)", example = "110550051877") String depositAccountNumber,
        @Schema(description = "예금주명 (마스킹 없는 원문, 지금 계좌 기준)", example = "홍길동") String payeeName,
        @Schema(description = "이 계좌로 마지막 성공 이체한 시각", example = "2026-11-10T14:30:00+09:00")
                OffsetDateTime lastTransferredAt,
        @Schema(description = "현재 이체 가능 여부. 계좌가 정지/해지되면 false", example = "true") boolean transferable) {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    public static RecentTransferAccountResponse from(RecentTransferAccountResult result) {
        return new RecentTransferAccountResponse(
                result.depositAccountNumber(),
                result.payeeName(),
                result.lastTransferredAt().atZone(KOREA_ZONE).toOffsetDateTime(),
                result.transferable());
    }
}
