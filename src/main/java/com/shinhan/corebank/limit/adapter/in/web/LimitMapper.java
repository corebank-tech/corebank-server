package com.shinhan.corebank.limit.adapter.in.web;

import com.shinhan.corebank.limit.application.port.in.dto.LimitResult;

/** 웹 계층 DTO 변환. 유스케이스 결과를 응답 형태로 옮긴다. */
public final class LimitMapper {

    private LimitMapper() {
    }

    public static LimitResponse toResponse(LimitResult result) {
        return new LimitResponse(
                result.oneTimeLimit(),
                result.dailyLimit(),
                result.dailyUsedAmount(),
                result.dailyRemainingAmount());
    }
}
