package com.shinhan.corebank.transfer.adapter.in.web;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.util.MaskingUtil;
import com.shinhan.corebank.transfer.application.port.in.InterbankTransferResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.time.ZoneId;

// 모양은 interbank_transfer_fe_handoff.md §5. transferredAt은 당행 응답과 달리 오프셋을 붙인다(REQ-CMN-016).
public record InterbankTransferResponse(
        @Schema(description = "SUCCESS · ERROR · TIMEOUT. TIMEOUT은 처리 불명이라 실패가 아니다 — 다시 보내지 말고 이체 내역으로 안내한다")
                ProcessResultStatus status,
        @Schema(description = "거래번호. 채번 전 실패면 null. TIMEOUT 건은 이 값으로 상세를 다시 조회한다", example = "20261023WB0000000123")
                String transactionNumber,
        @Schema(description = "이체 처리 시각(+09:00). SUCCESS일 때만 있다", nullable = true) OffsetDateTime transferredAt,
        @Schema(description = "받는 은행코드", example = "004") String payeeBankCode,
        @Schema(description = "상대 은행이 확인한 예금주(마스킹). SUCCESS일 때만 있다", example = "김*수", nullable = true) String payeeName,
        @Schema(description = "이체 후 출금계좌 잔액. SUCCESS일 때만 있다", nullable = true) Long withdrawalBalanceAfter,
        @Schema(description = "실패 코드. ERROR일 때만 있다", nullable = true) String errorCode,
        @Schema(description = "실패 메시지. ERROR일 때만 있다", nullable = true) String errorMessage) {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    public static InterbankTransferResponse from(InterbankTransferResult result) {
        return new InterbankTransferResponse(
                result.status(),
                result.transactionNumber(),
                result.transferredAt() == null
                        ? null
                        : result.transferredAt().atZone(KOREA_ZONE).toOffsetDateTime(),
                result.payeeBankCode(),
                result.payeeName() == null ? null : MaskingUtil.maskName(result.payeeName()),
                result.withdrawalBalanceAfter(),
                result.errorCode(),
                result.errorMessage());
    }
}
