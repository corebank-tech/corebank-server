package com.shinhan.corebank.signup.application.port.in;

// 회원가입 이메일 인증번호 발급 기능을 정의한다.
public interface IssueEmailVerificationUseCase {

    IssueEmailVerificationResult issue(IssueEmailVerificationCommand command);
}
