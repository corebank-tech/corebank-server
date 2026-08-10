package com.shinhan.corebank.autotransfer.adapter.in.web;

import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferChangeCommand;

import java.time.LocalDate;

public record AutoTransferChangeRequest(Long customerId, Long amount, Integer cycleMonths, LocalDate endDate, String myPassbookMemo,
                                        String recipientPassbookMemo,
                                        Long withdrawalAccountId, String depositAccountNumber, Integer transferDay,
                                        String authToken) {
    public AutoTransferChangeCommand toCommand(String requestIp) {
        return AutoTransferChangeCommand.builder()
                .customerId(customerId)
                .amount(amount)
                .cycleMonths(cycleMonths)
                .endDate(endDate)
                .myPassbookMemo(myPassbookMemo)
                .recipientPassbookMemo(recipientPassbookMemo)
                .withdrawalAccountId(withdrawalAccountId)
                .depositAccountNumber(depositAccountNumber)
                .transferDay(transferDay)
                .authToken(authToken)
                .requestIp(requestIp)
                .build();
    }
}
