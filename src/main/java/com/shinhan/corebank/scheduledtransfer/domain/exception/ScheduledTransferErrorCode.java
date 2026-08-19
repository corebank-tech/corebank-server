package com.shinhan.corebank.scheduledtransfer.domain.exception;

import com.shinhan.corebank.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ScheduledTransferErrorCode implements ErrorCode {
    INVALID_SCHEDULED_DATE("SCD0001", 400, "예약일자는 익일부터 1년 이내여야 합니다."),
    EXECUTION_DATE_PASSED("SCD0002", 400, "예약이체 실행일이 지났습니다."),
    INVALID_AMOUNT("SCD0005", 400, "이체금액은 0보다 커야 합니다."),
    MEMO_LENGTH_EXCEEDED("SCD0006", 400, "통장 표시내용은 10자 이내여야 합니다."),
    UNSUPPORTED_DEPOSIT_ACCOUNT_TYPE("SCD0007", 400, "입금계좌로 지정할 수 없는 계좌 유형입니다."),
    ONE_TIME_LIMIT_EXCEEDED("SCD0008", 400, "1회 이체한도를 초과했습니다."),
    NOT_FOUND("SCD0201", 404, "예약이체를 찾을 수 없습니다."),
    ACCOUNT_NOT_ACCESSIBLE("SCD0202", 404, "계좌를 확인할 수 없습니다."),
    DUPLICATE_REGISTRATION("SCD0301", 409, "동일 조건의 예약이체가 이미 등록되어 있습니다."),
    NOT_IN_WAITING_STATUS("SCD0302", 409, "대기 상태가 아닌 예약이체는 취소할 수 없습니다."),
    CANNOT_CANCEL_ON_EXECUTION_DATE("SCD0303", 409, "실행 예정일 당일에는 취소할 수 없습니다."),
    ALREADY_CANCELED("SCD0304", 409, "이미 취소된 예약이체입니다.");

    private final String code;
    private final int status;
    private final String message;
}
