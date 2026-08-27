package com.shinhan.corebank.autotransfer.adapter.out.auth;

import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferOtpVerificationPort;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerification;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
import com.shinhan.corebank.otp.api.OtpTransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AutoTransferOtpVerificationAdapter implements AutoTransferOtpVerificationPort {

    private final OtpAuthTokenVerifier otpAuthTokenVerifier;

    @Override
    public void verifyRegisterAndConsume(String otpAuthToken, Long customerId, Long withdrawalAccountId,
                                          String depositAccountNumber, Long amount, Integer cycleMonths,
                                          Integer transferDay, LocalDate startDate, LocalDate endDate) {
        Map<String, Object> transactionData = new LinkedHashMap<>();
        transactionData.put("withdrawalAccountId", withdrawalAccountId);
        transactionData.put("depositAccountNumber", depositAccountNumber);
        transactionData.put("amount", amount);
        transactionData.put("cycleMonths", cycleMonths);
        transactionData.put("transferDay", transferDay);
        transactionData.put("startDate", startDate.toString());
        transactionData.put("endDate", endDate.toString());
        verify(otpAuthToken, customerId, transactionData);
    }

    @Override
    public void verifyChangeAndConsume(String otpAuthToken, Long customerId, Long autoTransferId,
                                        Long amount, Integer cycleMonths, LocalDate endDate) {
        // null인 선택 필드는 넣지 않는다 — 발급 시점과 검증 시점의 거래정보가 정확히 일치해야 한다(otp_integration_guide.md)
        Map<String, Object> transactionData = new LinkedHashMap<>();
        transactionData.put("autoTransferId", autoTransferId);
        if (amount != null) {
            transactionData.put("amount", amount);
        }
        if (cycleMonths != null) {
            transactionData.put("cycleMonths", cycleMonths);
        }
        if (endDate != null) {
            transactionData.put("endDate", endDate.toString());
        }
        verify(otpAuthToken, customerId, transactionData);
    }

    // 거래정보를 단수 autoTransferId가 아니라 복수 autoTransferIds 배열로 담는다 —
    // 토큰 하나로 선택한 N건 전체를 인증하기 위한 것이다(corebank-server#330)
    @Override
    public void verifyCancelAndConsume(String otpAuthToken, Long customerId, List<Long> autoTransferIds) {
        verify(otpAuthToken, customerId, Map.of("autoTransferIds", autoTransferIds));
    }

    private void verify(String otpAuthToken, Long customerId, Map<String, Object> transactionData) {
        otpAuthTokenVerifier.verifyAndConsume(
                new OtpAuthTokenVerification(otpAuthToken, customerId, OtpTransactionType.AUTO_TRANSFER, transactionData));
    }
}
