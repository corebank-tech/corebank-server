package com.shinhan.corebank.signup.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

// 회원가입 약관 목록을 공통 items 필드로 반환한다.
public record SignupTermsResponse(@Schema(description = "현재 적용 중인 회원가입 약관 목록") List<SignupTermResponse> items) {}
