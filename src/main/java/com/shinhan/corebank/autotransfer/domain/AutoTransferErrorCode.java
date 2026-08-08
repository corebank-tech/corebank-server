package com.shinhan.corebank.autotransfer.domain;

import com.shinhan.corebank.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter //get...() 자동생성
@RequiredArgsConstructor    // final 필드 3개를 받는 생성자 자동 생성
public enum AutoTransferErrorCode implements ErrorCode {

    INVALID_TRANSFER_DAY("AUT0001", 400, "이체지정일은 1~31 사이여야 합니다."),
    INVALID_TRANSFER_PERIOD("AUT0002", 400, "이체 기간이 유효하지 않습니다. 시작일은 익일부터 1년 이내, 종료일은 시작일 이후 60개월 이내여야 합니다."),
    UNMODIFIABLE_FIELD("AUT0003", 400, "변경할 수 없는 항목입니다."),
    NO_EXECUTION_WITHIN_PERIOD("AUT0004", 400, "최초 이체 예정일이 종료일 이후입니다. 이체 기간 내에 최소 1회 이상 실행되어야 합니다."),
    UNSUPPORTED_DEPOSIT_ACCOUNT_TYPE("AUT0005",400,"입금계좌로 지정할 수 없는 계좌 유형입니다."),
    ONE_TIME_LIMIT_EXCEEDED("AUT0006", 400, "1회 이체한도를 초과했습니다."),
    NOT_FOUND("AUT0201", 404, "자동이체 등록 건을 찾을 수 없습니다."),
    ACCOUNT_NOT_ACCESSIBLE("AUT0202", 404, "입금계좌를 찾을 수 없습니다."),
    DUPLICATE_REGISTRATION("AUT0301", 409, "동일 조건의 자동이체가 이미 등록되어 있습니다."),
    NOT_IN_NORMAL_STATUS("AUT0302", 409, "정상 상태가 아닌 자동이체입니다."),
    CANNOT_TERMINATE_ON_EXECUTION_DATE("AUT0303", 409, "실행 예정일 당일에는 해지할 수 없습니다.");

    private final String code;
    private final int status;
    private final String message;

}
