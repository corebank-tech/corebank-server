package com.shinhan.corebank.limit.domain.exception;

import com.shinhan.corebank.common.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 이체한도 도메인 오류코드. 값은 docs/api_conventions.md §4-8 마스터를 따른다.
 * 한도 검증이 이체 실행 흐름 안에서 일어나는 동안에는 transfer 모듈의 임시
 * LimitErrorCode 가 같은 값을 던져 왔다. 그쪽 정리는 checkAndReserve 실구현
 * 시점에 함께 한다.
 */
@Getter
@RequiredArgsConstructor
public enum LmtErrorCode implements ErrorCode {

    INSUFFICIENT_WITHDRAWABLE_AMOUNT("LMT0001", 400, "출금가능금액이 부족합니다."),
    ONE_TIME_LIMIT_EXCEEDED("LMT0002", 400, "1회 이체한도를 초과했습니다."),
    DAILY_LIMIT_EXCEEDED("LMT0003", 400, "1일 이체한도를 초과했습니다."),
    ONE_TIME_LIMIT_OVER_DAILY("LMT0004", 400, "1회 한도는 1일 한도를 초과할 수 없습니다."),

    /**
     * 가입 연계(REQ-TRSF-029)와 백필이 모든 고객의 한도 행을 보장하므로 이 코드는 나올 수 없다.
     * 나온다면 사용자 잘못이 아니라 데이터 결함이라 500 이다 - TRF9001·ACC9001 과 같은 성격이다.
     */
    TRANSFER_LIMIT_NOT_FOUND("LMT9001", 500, "이체한도 정보를 확인할 수 없습니다.");

    private final String code;
    private final int status;
    private final String message;
}
