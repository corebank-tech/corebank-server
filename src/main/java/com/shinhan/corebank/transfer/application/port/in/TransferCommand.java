package com.shinhan.corebank.transfer.application.port.in;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.common.util.AccountNumberPolicy;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferType;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;
import java.time.LocalDate;
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
        // sourceId와 함께 멱등키를 구성하는 실행일자. SCHEDULED/AUTO 전용.
        // 배치가 실제로 execute()를 호출한 날짜가 아니라 그 회차의 논리적 실행일자를 넣어야 한다
        // — AUTO는 auto_transfer_execution.execution_date, SCHEDULED는 scheduled_transfer.scheduled_date
        // (#184 구현 시에도 동일). 그래야 크래시 후 재시도가 실제 호출 날짜와 무관하게 같은 회차로
        // 식별되어 멱등성 사전조회에 걸린다.
        LocalDate executionDate,
        String authToken, // Account-Password-Auth-Token. IMMEDIATE만 필수(REQ-TRSF-009) — SCHEDULED/AUTO는 시스템 트리거라 세션이 없음
        String otpAuthToken, // Otp-Auth-Token. IMMEDIATE만 필수 — SCHEDULED/AUTO는 등록 시점에 검증됨(otp_integration_guide.md)
        String flowId // FE 행동 흐름 식별자(선택). 훅 C(TransferHoldPolicy)에 그대로 넘긴다. IMMEDIATE만
        ) {

    private static final int FLOW_ID_MAX_LENGTH = 64;

    public TransferCommand {

        // 필수값 검증
        if (customerId == null
                || withdrawalAccountId == null
                || depositAccountNumber == null
                || transferType == null
                || channel == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }

        // 계좌번호 형식 검증
        if (!AccountNumberPolicy.ACCOUNT_NUMBER_PATTERN
                .matcher(depositAccountNumber)
                .matches()) {
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

        // 예약·자동이체는 원본 식별자(sourceId)와 회차 실행일자(executionDate)가 필수다
        if ((transferType == TransferType.SCHEDULED || transferType == TransferType.AUTO)
                && (sourceId == null || executionDate == null)) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }

        if (transferType == TransferType.IMMEDIATE) {
            validateImmediate(sourceId, executionDate, authToken, otpAuthToken);
        }

        // 화면 행동은 고객이 직접 시작한 즉시이체에만 있다. 예약·자동이체에 오면 잘못된 호출이다
        if (flowId != null
                && (transferType != TransferType.IMMEDIATE
                        || flowId.isBlank()
                        || flowId.length() > FLOW_ID_MAX_LENGTH)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }

    // 즉시이체는 고객 요청(토큰 둘, sourceId 없음)이거나 보류 건 실행(sourceId·실행일, 토큰 없음)이다(§9-4).
    private static void validateImmediate(
            Long sourceId, LocalDate executionDate, String authToken, String otpAuthToken) {
        if (sourceId != null) {
            if (executionDate == null) {
                throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
            }
            if (authToken != null || otpAuthToken != null) {
                throw new BusinessException(CommonErrorCode.INVALID_INPUT);
            }
            return;
        }
        if (executionDate != null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        // 고객 요청은 두 토큰이 모두 있어야 한다(REQ-TRSF-009)
        if (authToken == null || authToken.isBlank() || otpAuthToken == null || otpAuthToken.isBlank()) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
    }
}
