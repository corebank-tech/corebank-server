package com.shinhan.corebank.scheduledtransfer.adapter.out.auth;

import com.shinhan.corebank.otp.api.OtpAuthTokenVerification;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
import com.shinhan.corebank.otp.api.OtpTransactionType;
import com.shinhan.corebank.scheduledtransfer.application.port.out.ScheduledTransferOtpVerificationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ScheduledTransferOtpVerificationAdapter implements ScheduledTransferOtpVerificationPort {

    private final OtpAuthTokenVerifier otpAuthTokenVerifier;

    @Override
    public void verifyRegisterAndConsume(String otpAuthToken, Long customerId, Long withdrawalAccountId,
                                          String depositAccountNumber, Long amount, LocalDate scheduledDate) {
        otpAuthTokenVerifier.verifyAndConsume(new OtpAuthTokenVerification(
                otpAuthToken, customerId, OtpTransactionType.SCHEDULED_TRANSFER,
                Map.of(
                        "withdrawalAccountId", withdrawalAccountId,
                        "depositAccountNumber", depositAccountNumber,
                        "amount", amount,
                        "scheduledDate", scheduledDate.toString()
                )
        ));
    }

    @Override
    public void verifyCancelAndConsume(String otpAuthToken, Long customerId, Long scheduledTransferId) {
        otpAuthTokenVerifier.verifyAndConsume(new OtpAuthTokenVerification(
                otpAuthToken, customerId, OtpTransactionType.SCHEDULED_TRANSFER,
                Map.of("scheduledTransferId", scheduledTransferId)
        ));
    }
}
