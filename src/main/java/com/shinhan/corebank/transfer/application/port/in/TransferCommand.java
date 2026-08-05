package com.shinhan.corebank.transfer.application.port.in;

import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferType;
import lombok.Builder;

@Builder
public record TransferCommand(
    Long withdrawalAccountId,
    String depositAccountNumber,
    long amount,
    TransferType transferType,
    TransferChannel channel,
    String myPassbookMemo,
    String recipientPassbookMemo
) {
}
