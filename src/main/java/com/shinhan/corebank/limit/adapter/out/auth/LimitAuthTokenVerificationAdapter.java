package com.shinhan.corebank.limit.adapter.out.auth;

import java.util.Map;

import com.shinhan.corebank.limit.application.port.out.AuthTokenVerificationPort;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerification;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
import com.shinhan.corebank.otp.api.OtpTransactionType;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 인증 토큰 검증을 P6 의 공개 API 에 위임한다.
 *
 * <p>OTP 는 otp/api 의 OtpAuthTokenVerifier 가 이미 구현돼 있어 그대로 호출한다. 계좌비밀번호는
 * 1차 범위 밖이라 검증하지 않는다 - transfer·autotransfer·scheduledtransfer·account 도 같다.
 *
 * <p>transactionData 의 키는 이 어댑터가 정한다. FE 는 OTP 발급(POST /otp) 시 같은 키·값을
 * 보내야 하며, 다르면 OTP0102 로 거부된다.
 */
@Component
@RequiredArgsConstructor
public class LimitAuthTokenVerificationAdapter implements AuthTokenVerificationPort {

    private final OtpAuthTokenVerifier otpAuthTokenVerifier;

    @Override
    public void verifyAccountPassword(String authToken, Long customerId) {
        // 1차 범위는 OTP 인증까지다. 계좌비밀번호 검증은 2차에 붙는다 - 그때 P6 의 공개 API 에 위임한다.
    }

    @Override
    public void verifyOtp(String otpAuthToken, Long customerId, long oneTimeLimit, long dailyLimit) {
        otpAuthTokenVerifier.verifyAndConsume(new OtpAuthTokenVerification(
                otpAuthToken,
                customerId,
                OtpTransactionType.TRANSFER_LIMIT_CHANGE,
                Map.of("oneTimeLimit", oneTimeLimit, "dailyLimit", dailyLimit)));
    }
}
