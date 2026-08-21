package com.shinhan.corebank.autotransfer.application.port.in;

import com.shinhan.corebank.autotransfer.domain.AutoTransferErrorCode;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import lombok.Builder;

import java.time.LocalDate;

@Builder
public record AutoTransferChangeCommand(Long customerId, Long amount, Integer cycleMonths, LocalDate endDate,
                                        String myPassbookMemo, String recipientPassbookMemo,
                                        Long withdrawalAccountId, String depositAccountNumber, Integer transferDay,
                                        String accountPasswordAuthToken, String otpAuthToken, String requestIp) {
    public AutoTransferChangeCommand {
        if (customerId == null || accountPasswordAuthToken == null || otpAuthToken == null || requestIp == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
        // 출금계좌·입금계좌·이체지정일은 변경 불가(REQ-AUTO-010) — 값이 오면 무시하지 않고 명시적으로 거부한다
        if (withdrawalAccountId != null || depositAccountNumber != null || transferDay != null) {
            throw new BusinessException(AutoTransferErrorCode.UNMODIFIABLE_FIELD);
        }
        // amount는 선택(null=미변경)이지만, 보냈다면 유효한 값이어야 한다
        if (amount != null && amount <= 0) {
            throw new BusinessException(AutoTransferErrorCode.INVALID_AMOUNT);
        }
        // 메모 길이 검증
        if (myPassbookMemo != null && myPassbookMemo.length() > 10) {
            throw new BusinessException(AutoTransferErrorCode.MEMO_LENGTH_EXCEEDED);
        }
        if (recipientPassbookMemo != null && recipientPassbookMemo.length() > 10) {
            throw new BusinessException(AutoTransferErrorCode.MEMO_LENGTH_EXCEEDED);
        }
        //공백 문자열 검증
        if (accountPasswordAuthToken.isBlank() || otpAuthToken.isBlank() || requestIp.isBlank()) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
    }
}
