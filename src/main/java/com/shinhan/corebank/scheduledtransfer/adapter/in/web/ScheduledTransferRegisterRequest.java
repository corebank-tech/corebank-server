package com.shinhan.corebank.scheduledtransfer.adapter.in.web;

import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferRegisterCommand;

import java.time.LocalDate;

public record ScheduledTransferRegisterRequest(Long withdrawalAccountId, String depositAccountNumber,
                                                String payeeName, Long amount, LocalDate scheduledDate,
                                                String myPassbookMemo, String recipientPassbookMemo,
                                                String accountPasswordAuthToken, String otpAuthToken) {
    public ScheduledTransferRegisterCommand toCommand(String requestIp, Long customerId) {
        return ScheduledTransferRegisterCommand.builder()
                .customerId(customerId)
                .withdrawalAccountId(withdrawalAccountId)
                .depositAccountNumber(depositAccountNumber)
                .payeeName(payeeName)
                .amount(amount)
                .scheduledDate(scheduledDate)
                .myPassbookMemo(myPassbookMemo)
                .recipientPassbookMemo(recipientPassbookMemo)
                .accountPasswordAuthToken(accountPasswordAuthToken)
                .otpAuthToken(otpAuthToken)
                .requestIp(requestIp)
                .build();
    }
}
