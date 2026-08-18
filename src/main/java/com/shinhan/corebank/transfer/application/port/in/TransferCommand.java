package com.shinhan.corebank.transfer.application.port.in;

import java.util.regex.Pattern;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferType;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;

import lombok.Builder;

@Builder
public record TransferCommand(
    Long customerId, // 출금계좌 소유권·한도 검증 대상 고객
    Long withdrawalAccountId,
    String depositAccountNumber,
    long amount,
    TransferType transferType,
    TransferChannel channel,
    String myPassbookMemo,
    String recipientPassbookMemo,
    Long sourceId, // 이체를 발생시킨 원본 거래 역추적 목적으로 사용
    String authToken // Account-Password-Auth-Token. IMMEDIATE만 필수(REQ-TRSF-009) — SCHEDULED/AUTO는 시스템 트리거라 세션이 없음
) {

    private static final Pattern ACCOUNT_NUMBER_PATTERN = Pattern.compile("^[0-9]{12}$");

    public TransferCommand {

        // 필수값 검증
        if (customerId == null || withdrawalAccountId == null || depositAccountNumber == null ||
                transferType == null || channel == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }

        // 계좌번호 형식 검증
        if (!ACCOUNT_NUMBER_PATTERN.matcher(depositAccountNumber).matches()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        // 비즈니스 유효성 검증
        if (amount <= 0) {
            throw new BusinessException(TransferErrorCode.INVALID_AMOUNT);
        }
        if (myPassbookMemo != null && myPassbookMemo.length() > 10) {
            throw new BusinessException(TransferErrorCode.MEMO_LENGTH_EXCEEDED);
        }
        if (recipientPassbookMemo != null && recipientPassbookMemo.length() > 10) {
            throw new BusinessException(TransferErrorCode.MEMO_LENGTH_EXCEEDED);
        }

        // 이체 유형에 따른 원본 식별자(sourceId) 필수 여부 검증
        if (transferType == TransferType.IMMEDIATE && sourceId != null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        if ((transferType == TransferType.SCHEDULED || transferType == TransferType.AUTO)
                && sourceId == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }

        // 즉시 이체만 인증 토큰 필수. 예약/자동 이체는 시스템(배치)이 트리거하므로 실시간
        // 사용자 세션이 없어 인증 토큰을 받을 수 없다.
        if (transferType == TransferType.IMMEDIATE && (authToken == null || authToken.isBlank())) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
    }
}
