package com.shinhan.corebank.transfer.application.port.out;

public interface TransferOtpVerificationPort {

    void verifyAndConsume(
            String otpAuthToken, Long customerId, Long withdrawalAccountId, String depositAccountNumber, long amount);

    // 타행은 대조 데이터에 은행코드가 더해져 당행용 OTP를 쓸 수 없다(interbank_transfer_fe_handoff.md §4-3).
    void verifyAndConsumeInterbank(
            String otpAuthToken,
            Long customerId,
            Long withdrawalAccountId,
            String payeeBankCode,
            String depositAccountNumber,
            long amount);
}
