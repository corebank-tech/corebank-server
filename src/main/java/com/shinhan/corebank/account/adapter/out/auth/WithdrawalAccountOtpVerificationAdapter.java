package com.shinhan.corebank.account.adapter.out.auth;

import com.shinhan.corebank.account.application.port.out.WithdrawalAccountOtpVerificationPort;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerification;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
import com.shinhan.corebank.otp.api.OtpTransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class WithdrawalAccountOtpVerificationAdapter
        implements WithdrawalAccountOtpVerificationPort {

    private final OtpAuthTokenVerifier otpAuthTokenVerifier;

    @Override
    public void verifyAndConsume(
            String otpAuthToken,
            Long customerId,
            Long accountId
    ) {
        otpAuthTokenVerifier.verifyAndConsume(
                new OtpAuthTokenVerification(
                        otpAuthToken,
                        customerId,
                        OtpTransactionType.WITHDRAWAL_ACCOUNT_REGISTER,
                        Map.of("accountId", accountId)
                )
        );
    }
}
