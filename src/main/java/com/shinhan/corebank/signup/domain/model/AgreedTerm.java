package com.shinhan.corebank.signup.domain.model;

// 사용자가 동의한 약관 식별자와 버전을 표현한다.
public record AgreedTerm(String termsId, String version) {}
