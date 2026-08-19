package com.shinhan.corebank.signup.adapter.in.web.dto;

import com.shinhan.corebank.signup.application.port.in.CheckUserIdResult;

// 아이디 사용 가능 여부와 중복확인 완료 토큰을 반환한다.
public record CheckUserIdResponse(
        boolean isAvailable,
        String userIdCheckToken,
        long expiresIn
) {

    public static CheckUserIdResponse from(CheckUserIdResult result) {
        return new CheckUserIdResponse(
                result.isAvailable(),
                result.userIdCheckToken(),
                result.expiresIn()
        );
    }
}
