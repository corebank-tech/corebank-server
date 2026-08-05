package com.shinhan.corebank.transfer.application.port.in;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferType;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;

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
    public TransferCommand {
        if (amount <= 0) {
            throw new BusinessException(TransferErrorCode.INVALID_AMOUNT);
        }
        if (myPassbookMemo != null && myPassbookMemo.length() > 10) {
            throw new BusinessException(TransferErrorCode.MEMO_LENGTH_EXCEEDED);
        }
        if (recipientPassbookMemo != null && recipientPassbookMemo.length() > 10) {
            throw new BusinessException(TransferErrorCode.MEMO_LENGTH_EXCEEDED);
        }
    }
}
