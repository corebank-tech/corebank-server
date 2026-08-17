package com.shinhan.corebank.transfer.adapter.in.web;

import com.shinhan.corebank.transfer.application.port.in.TransferCommand;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferType;

public record TransferRequest(
        Long withdrawalAccountId,
        String depositAccountNumber,
        long amount,
        String myPassbookMemo,
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
