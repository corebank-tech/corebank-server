package com.shinhan.corebank.transfer.domain.exception;

import com.shinhan.corebank.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 1회/1일 한도 초과는 이제 limit.domain.exception.LmtErrorCode가 던진다 - 중복 정의라 제거.
@Getter
@RequiredArgsConstructor
public enum LimitErrorCode implements ErrorCode {
    INSUFFICIENT_WITHDRAWABLE_AMOUNT("LMT0001", 400, "출금가능금액이 부족합니다.");

    private final String code;
    private final int status;
    private final String message;
}
