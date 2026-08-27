package com.shinhan.corebank.scheduledtransfer.adapter.out.auth;

import com.shinhan.corebank.otp.api.OtpAuthTokenVerification;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
import com.shinhan.corebank.otp.api.OtpTransactionType;
import com.shinhan.corebank.scheduledtransfer.application.port.out.ScheduledTransferOtpVerificationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
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

    // 거래정보를 단수 scheduledTransferId가 아니라 복수 scheduledTransferIds 배열로 담는다 —
    // 토큰 하나로 선택한 N건 전체를 인증하기 위한 것이다(corebank-server#330)
    @Override
    public void verifyCancelAndConsume(String otpAuthToken, Long customerId, List<Long> scheduledTransferIds) {
        otpAuthTokenVerifier.verifyAndConsume(new OtpAuthTokenVerification(
                otpAuthToken, customerId, OtpTransactionType.SCHEDULED_TRANSFER,
                Map.of("scheduledTransferIds", scheduledTransferIds)
        ));
    }
}
