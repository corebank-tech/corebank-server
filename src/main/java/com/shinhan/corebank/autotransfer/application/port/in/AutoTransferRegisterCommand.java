package com.shinhan.corebank.autotransfer.application.port.in;

import static com.shinhan.corebank.common.util.AccountNumberPolicy.ACCOUNT_NUMBER_PATTERN;

import com.shinhan.corebank.autotransfer.domain.AutoTransferErrorCode;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.time.LocalDate;
import lombok.Builder;

@Builder
// register()에 도달하기 전 단계에서 미리 걸러내는 역할을 하는 파일
public record AutoTransferRegisterCommand(
        Long customerId,
        Long withdrawalAccountId,
        String depositAccountNumber,
        String payeeName,
        Long amount,
        Integer cycleMonths,
        Integer transferDay,
        LocalDate startDate,
        LocalDate endDate,
        String myPassbookMemo,
        String recipientPassbookMemo,
        String accountPasswordAuthToken,
        String otpAuthToken,
        String requestIp) {

    public AutoTransferRegisterCommand {
        // 필수값 검증
        if (customerId == null
                || withdrawalAccountId == null
                || depositAccountNumber == null
                || payeeName == null
                || amount == null
                || cycleMonths == null
                || transferDay == null
                || startDate == null
                || endDate == null
                || accountPasswordAuthToken == null
                || otpAuthToken == null
                || requestIp == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
        // 계좌번호 형식 검증
        if (!ACCOUNT_NUMBER_PATTERN.matcher(depositAccountNumber).matches()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        // 금액 검증
        if (amount <= 0) {
            throw new BusinessException(AutoTransferErrorCode.INVALID_AMOUNT);
        }
        // 메모 길이 검증
        if (myPassbookMemo != null && myPassbookMemo.length() > 10) {
            throw new BusinessException(AutoTransferErrorCode.MEMO_LENGTH_EXCEEDED);
        }
        if (recipientPassbookMemo != null && recipientPassbookMemo.length() > 10) {
            throw new BusinessException(AutoTransferErrorCode.MEMO_LENGTH_EXCEEDED);
        }
        // 공백 문자열 검증
        if (payeeName.isBlank()
                || accountPasswordAuthToken.isBlank()
                || otpAuthToken.isBlank()
                || requestIp.isBlank()) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
    }
}
