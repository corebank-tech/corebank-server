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
    UNSUPPORTED_BANK("TRF0006", 400, "지원하지 않는 은행입니다."),
    INVALID_INTERBANK_ACCOUNT_NUMBER("TRF0007", 400, "타행 계좌번호는 숫자 1~16자리여야 합니다."),
    UNSENDABLE_MEMO_CHARACTER("TRF0008", 400, "통장 표시내용에 보낼 수 없는 문자가 있습니다."),
    PAYEE_NOT_FOUND("TRF0201", 404, "입금계좌를 찾을 수 없습니다."),
    TRANSACTION_NOT_FOUND("TRF0202", 404, "거래내역을 찾을 수 없습니다."),
    PAYEE_ACCOUNT_SUSPENDED("TRF0301", 409, "거래정지 또는 해지 상태의 입금계좌입니다."),
    INVALID_STATUS_TRANSITION("TRF0302", 409, "이미 처리 완료(SUCCESS/ERROR)된 이체는 상태를 변경할 수 없습니다."),
    INSUFFICIENT_BALANCE("TRF0303", 409, "출금계좌 잔액이 부족합니다."),
    WITHDRAWAL_ACCOUNT_SUSPENDED("TRF0304", 409, "거래정지 또는 해지 상태의 출금계좌입니다."),
    NOT_CORRECTABLE("TRF0305", 409, "정정할 수 없는 이체입니다."),
    REVERSAL_INSUFFICIENT_BALANCE("TRF0306", 409, "입금계좌 잔액이 부족해 이체를 되돌릴 수 없습니다."),
    DEPOSIT_TYPE_STATUS_MISMATCH("TRF0307", 409, "입금 유형과 계좌 상태가 맞지 않습니다."),
    INTERBANK_NOT_PROCESSED("TRF0308", 409, "상대 은행에서 이체를 처리하지 못했습니다."),
    INTERBANK_PAYEE_UNVERIFIED("TRF0309", 409, "상대 은행에서 예금주를 확인하지 못했습니다."),
    ACCOUNT_LOCK_TARGET_NOT_FOUND("TRF9001", 500, "이체 처리 중 계좌 정보를 확인할 수 없습니다."),
    TRANSACTION_SEQUENCE_EXHAUSTED("TRF9002", 500, "거래번호 일련번호 채번 가능 범위를 초과했습니다.");

    private final String code;
    private final int status;
    private final String message;
}
