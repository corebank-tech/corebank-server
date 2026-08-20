package com.shinhan.corebank.transfer.adapter.in.web;

import com.shinhan.corebank.transfer.application.port.in.TransferCommand;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferType;

import io.swagger.v3.oas.annotations.media.Schema;

public record TransferRequest(
        @Schema(description = "출금계좌 ID (내 계좌)", example = "1001")
        Long withdrawalAccountId,
        @Schema(description = "입금계좌번호 (하이픈 없이)", example = "11012345678901")
        String depositAccountNumber,
        @Schema(description = "이체금액. 1원 이상의 정수", example = "50000", minimum = "1")
        long amount,
        @Schema(description = "내 통장에 남길 표시내용. 최대 10자", example = "생활비", maxLength = 10)
        String myPassbookMemo,
        @Schema(description = "상대 통장에 남길 표시내용. 최대 10자", example = "홍길동", maxLength = 10)
        String recipientPassbookMemo
) {
    public TransferCommand toCommand(Long customerId, String authToken) {
        return TransferCommand.builder()
                .customerId(customerId)
                .withdrawalAccountId(withdrawalAccountId)
                .depositAccountNumber(depositAccountNumber)
                .amount(amount)
                .transferType(TransferType.IMMEDIATE)
                .channel(TransferChannel.WB)
                .myPassbookMemo(myPassbookMemo)
                .recipientPassbookMemo(recipientPassbookMemo)
                .authToken(authToken)
                .build();
    }
}
