package com.shinhan.corebank.autotransfer.application.port.out;

import java.time.LocalDate;

public interface AutoTransferOtpVerificationPort {

    void verifyRegisterAndConsume(String otpAuthToken, Long customerId, Long withdrawalAccountId,
                                   String depositAccountNumber, Long amount, Integer cycleMonths,
                                   Integer transferDay, LocalDate startDate, LocalDate endDate);

    void verifyChangeAndConsume(String otpAuthToken, Long customerId, Long autoTransferId,
                                 Long amount, Integer cycleMonths, LocalDate endDate);

    void verifyCancelAndConsume(String otpAuthToken, Long customerId, Long autoTransferId);
}
