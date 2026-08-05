package com.shinhan.corebank.transfer.domain.exception;

import com.shinhan.corebank.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TransferErrorCode implements ErrorCode {
    
    WITHDRAWAL_ACCOUNT_NOT_REGISTERED("TRF0001", 400, "등록되지 않은 출금계좌입니다."),
    SAME_ACCOUNT_TRANSFER("TRF0002", 400, "출금계좌와 입금계좌가 동일합니다."),
    INVALID_AMOUNT("TRF0003", 400, "이체금액은 1원 이상의 정수여야 합니다."),
    UNSUPPORTED_ACCOUNT_TYPE("TRF0004", 400, "입금계좌로 지정할 수 없는 상품 유형입니다."),
    MEMO_LENGTH_EXCEEDED("TRF0005", 400, "통장 표시내용 길이 제한을 초과했습니다."),
    PAYEE_NOT_FOUND("TRF0201", 404, "입금계좌를 찾을 수 없습니다."),
    TRANSACTION_NOT_FOUND("TRF0202", 404, "거래내역을 찾을 수 없습니다."),
    PAYEE_ACCOUNT_SUSPENDED("TRF0301", 409, "거래정지 또는 해지 상태의 입금계좌입니다.");

    private final String code;
    private final int status;
    private final String message;


}
