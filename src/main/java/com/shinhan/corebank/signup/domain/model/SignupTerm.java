package com.shinhan.corebank.signup.domain.model;

// 회원가입 화면에 제공할 현재 약관 정보를 표현한다.
public record SignupTerm(
        String termsId,
        String termsCode,
        String version,
        String title,
        String content,
        boolean required,
        boolean viewRequired) {}
