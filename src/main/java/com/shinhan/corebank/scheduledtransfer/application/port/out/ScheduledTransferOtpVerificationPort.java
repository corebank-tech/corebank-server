package com.shinhan.corebank.scheduledtransfer.application.port.out;

import java.time.LocalDate;

public interface ScheduledTransferOtpVerificationPort {

    void verifyRegisterAndConsume(String otpAuthToken, Long customerId, Long withdrawalAccountId,
                                   String depositAccountNumber, Long amount, LocalDate scheduledDate);

    void verifyCancelAndConsume(String otpAuthToken, Long customerId, Long scheduledTransferId);
}
