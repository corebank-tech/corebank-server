package com.shinhan.corebank.transfer.adapter.out.auth;

import com.shinhan.corebank.otp.api.OtpAuthTokenVerification;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
import com.shinhan.corebank.otp.api.OtpTransactionType;
import com.shinhan.corebank.transfer.application.port.out.TransferOtpVerificationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class TransferOtpVerificationAdapter implements TransferOtpVerificationPort {

    private final OtpAuthTokenVerifier otpAuthTokenVerifier;

    @Override
    public void verifyAndConsume(
            String otpAuthToken,
            Long customerId,
            Long withdrawalAccountId,
            String depositAccountNumber,
            long amount
    ) {
        otpAuthTokenVerifier.verifyAndConsume(
                new OtpAuthTokenVerification(
                        otpAuthToken,
                        customerId,
                        OtpTransactionType.IMMEDIATE_TRANSFER,
                        Map.of(
                                "withdrawalAccountId", withdrawalAccountId,
                                "depositAccountNumber", depositAccountNumber,
                                "amount", amount
                        )
                )
        );
    }
}
