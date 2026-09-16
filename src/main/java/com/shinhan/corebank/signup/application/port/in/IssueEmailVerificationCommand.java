package com.shinhan.corebank.signup.application.port.in;

import com.shinhan.corebank.signup.domain.model.EmailVerificationPurpose;

// 이메일 인증번호 발급에 필요한 이메일과 목적을 전달한다.
public record IssueEmailVerificationCommand(String email, EmailVerificationPurpose purpose) {}
