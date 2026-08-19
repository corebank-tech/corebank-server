package com.shinhan.corebank.scheduledtransfer.adapter.in.web;

import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferRegisterCommand;

import java.time.LocalDate;

public record ScheduledTransferRegisterRequest(Long withdrawalAccountId, String payeeAccountNumber,
                                                String payeeName, Long amount, LocalDate scheduledDate,
                                                String myPassbookMemo, String recipientPassbookMemo,
                                                String accountPasswordAuthToken) {
    public ScheduledTransferRegisterCommand toCommand(String requestIp, Long customerId) {
        return ScheduledTransferRegisterCommand.builder()
                .customerId(customerId)
                .withdrawalAccountId(withdrawalAccountId)
                .payeeAccountNumber(payeeAccountNumber)
                .payeeName(payeeName)
                .amount(amount)
                .scheduledDate(scheduledDate)
                .myPassbookMemo(myPassbookMemo)
                .recipientPassbookMemo(recipientPassbookMemo)
                .accountPasswordAuthToken(accountPasswordAuthToken)
                .requestIp(requestIp)
                .build();
    }
}
