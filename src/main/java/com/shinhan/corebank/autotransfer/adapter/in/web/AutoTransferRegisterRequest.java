package com.shinhan.corebank.autotransfer.adapter.in.web;

import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferRegisterCommand;

import java.time.LocalDate;

public record AutoTransferRegisterRequest (Long customerId, Long withdrawalAccountId, String depositAccountNumber,
                                           String payeeName, Long amount, Integer cycleMonths, Integer transferDay,
                                           LocalDate startDate, LocalDate endDate, String myPassbookMemo, String recipientPassbookMemo,
                                           String authToken  ){
    public AutoTransferRegisterCommand toCommand() {
        return AutoTransferRegisterCommand.builder()
                .customerId(customerId)
                .withdrawalAccountId(withdrawalAccountId)
                .depositAccountNumber(depositAccountNumber)
                .payeeName(payeeName)
                .amount(amount)
                .cycleMonths(cycleMonths)
                .transferDay(transferDay)
                .startDate(startDate)
                .endDate(endDate)
                .myPassbookMemo(myPassbookMemo)
                .recipientPassbookMemo(recipientPassbookMemo)
                .authToken(authToken)
                .build();
    }
}
