package com.shinhan.corebank.info.domain.exception;

import com.shinhan.corebank.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 정보계 조회 도메인 오류코드. 값은 docs/api_conventions.md §4-15 마스터를 따른다. */
@Getter
@RequiredArgsConstructor
public enum InfoErrorCode implements ErrorCode {
    CONNECTION_FAILED("INF9001", 500, "정보계 API에 연결하지 못했습니다.");

    private final String code;
    private final int status;
    private final String message;
}
