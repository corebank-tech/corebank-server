package com.shinhan.corebank.signup.adapter.in.web.dto;

import java.util.List;

// 회원가입 약관 목록을 공통 items 필드로 반환한다.
public record SignupTermsResponse(
        List<SignupTermResponse> items
) {
}
