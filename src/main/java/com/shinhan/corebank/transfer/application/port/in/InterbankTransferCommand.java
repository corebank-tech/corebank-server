package com.shinhan.corebank.transfer.application.port.in;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.common.util.AccountNumberPolicy;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;
import java.nio.charset.Charset;
import lombok.Builder;

@Builder
public record InterbankTransferCommand(
        Long customerId,
        Long withdrawalAccountId,
        String payeeBankCode,
        String depositAccountNumber,
        long amount,
        String myPassbookMemo,
        String recipientPassbookMemo,
        String authToken,
        String otpAuthToken) {

    // 전문 문자셋(fixed_length_message_spec.md §1). 직렬화도 막지만 출금 전에 먼저 거른다(§4).
    private static final Charset MESSAGE_CHARSET = Charset.forName("MS949");
    private static final int MEMO_MAX_LENGTH = 10;

    public InterbankTransferCommand {
        if (customerId == null
                || withdrawalAccountId == null
                || payeeBankCode == null
                || depositAccountNumber == null
                || authToken == null
                || authToken.isBlank()
                || otpAuthToken == null
                || otpAuthToken.isBlank()) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
        if (!AccountNumberPolicy.INTERBANK_ACCOUNT_NUMBER_PATTERN
                .matcher(depositAccountNumber)
                .matches()) {
            throw new BusinessException(TransferErrorCode.INVALID_INTERBANK_ACCOUNT_NUMBER);
        }
        if (amount <= 0) {
            throw new BusinessException(TransferErrorCode.INVALID_AMOUNT);
        }
        if (exceedsMemoLength(myPassbookMemo) || exceedsMemoLength(recipientPassbookMemo)) {
            throw new BusinessException(TransferErrorCode.MEMO_LENGTH_EXCEEDED);
        }
        // 받는 분 통장 표시만 전문에 실린다.
        if (recipientPassbookMemo != null && !MESSAGE_CHARSET.newEncoder().canEncode(recipientPassbookMemo)) {
            throw new BusinessException(TransferErrorCode.UNSENDABLE_MEMO_CHARACTER);
        }
    }

    private static boolean exceedsMemoLength(String memo) {
        return memo != null && memo.length() > MEMO_MAX_LENGTH;
    }
}
