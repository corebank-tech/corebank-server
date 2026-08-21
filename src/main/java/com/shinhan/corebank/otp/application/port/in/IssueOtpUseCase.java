package com.shinhan.corebank.otp.application.port.in;

// OTP 발급과 기존 활성 OTP 무효화를 수행하는 인바운드 유스케이스다.
public interface IssueOtpUseCase {
    IssueOtpResult issue(IssueOtpCommand command);
}
