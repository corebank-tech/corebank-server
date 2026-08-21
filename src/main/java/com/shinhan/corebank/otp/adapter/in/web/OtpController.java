package com.shinhan.corebank.otp.adapter.in.web;

import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.otp.application.port.in.IssueOtpResult;
import com.shinhan.corebank.otp.application.port.in.IssueOtpUseCase;
import com.shinhan.corebank.otp.application.port.in.VerifyOtpResult;
import com.shinhan.corebank.otp.application.port.in.VerifyOtpUseCase;
import com.shinhan.corebank.otp.config.OtpProperties;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 로그인 고객에게 OTP 발급과 숫자 6자리 검증 API를 제공한다.
@RestController
@RequestMapping("/otp")
@RequiredArgsConstructor
public class OtpController {

    private final CurrentCustomerProvider currentCustomerProvider;
    private final IssueOtpUseCase issueOtpUseCase;
    private final VerifyOtpUseCase verifyOtpUseCase;
    private final OtpProperties otpProperties;

    @PostMapping("/issue")
    public ApiResponse<IssueOtpResponse> issue(
            @Valid @RequestBody IssueOtpRequest request
    ) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        IssueOtpResult result = issueOtpUseCase.issue(request.toCommand(customerId));
        return ApiResponse.success(
                IssueOtpResponse.from(result, otpProperties.exposeCode()),
                "OTP가 성공적으로 발급되었습니다."
        );
    }

    @PostMapping("/verify")
    public ApiResponse<VerifyOtpResponse> verify(
            @Valid @RequestBody VerifyOtpRequest request
    ) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        VerifyOtpResult result = verifyOtpUseCase.verify(request.toCommand(customerId));
        return ApiResponse.success(
                VerifyOtpResponse.success(result),
                "OTP 검증이 완료되었습니다."
        );
    }
}
