package com.shinhan.corebank.auth.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

// 본인확인에 성공한 고객의 전체 로그인 아이디를 응답한다.
public record FindIdResponse(
        @Schema(description = "조회된 전체 로그인 아이디", example = "DUDGNS389")
        String userId
) {
}
