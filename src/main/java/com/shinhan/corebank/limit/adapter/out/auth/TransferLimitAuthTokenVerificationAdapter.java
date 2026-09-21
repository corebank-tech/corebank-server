package com.shinhan.corebank.limit.adapter.out.auth;

import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerifier;
import com.shinhan.corebank.limit.application.port.out.AuthTokenVerificationPort;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerification;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
import com.shinhan.corebank.otp.api.OtpTransactionType;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 인증 토큰 검증을 P6 의 공개 API 에 위임한다.
 *
 * <p>이체한도는 고객 단위 자원이므로 계좌비밀번호 토큰의 customerId만 세션 고객과 대조한다.
 *
 * <p>transactionData 의 키는 이 어댑터가 정한다. FE 는 OTP 발급(POST /otp) 시 같은 키·값을
 * 보내야 하며, 다르면 OTP0102 로 거부된다.
 */
@Component
@RequiredArgsConstructor
public class TransferLimitAuthTokenVerificationAdapter implements AuthTokenVerificationPort {

    private final AccountPasswordAuthTokenVerifier accountPasswordAuthTokenVerifier;
    private final OtpAuthTokenVerifier otpAuthTokenVerifier;

    @Override
    public void verifyAccountPassword(String authToken, Long customerId) {
        accountPasswordAuthTokenVerifier.verifyAndConsume(authToken, customerId);
    }

    @Override
    public void verifyAndConsumeOtp(String otpAuthToken, Long customerId, long oneTimeLimit, long dailyLimit) {
        otpAuthTokenVerifier.verifyAndConsume(new OtpAuthTokenVerification(
                otpAuthToken,
                customerId,
                OtpTransactionType.TRANSFER_LIMIT_CHANGE,
                Map.of("oneTimeLimit", oneTimeLimit, "dailyLimit", dailyLimit)));
    }
}
