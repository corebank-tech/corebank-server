package com.shinhan.corebank.subscription.adapter.out.auth;

import com.shinhan.corebank.otp.api.OtpAuthTokenVerification;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
import com.shinhan.corebank.otp.api.OtpTransactionType;
import com.shinhan.corebank.subscription.application.port.out.ProductSubscriptionOtpVerificationPort;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductSubscriptionOtpVerificationAdapter implements ProductSubscriptionOtpVerificationPort {

    private final OtpAuthTokenVerifier otpAuthTokenVerifier;

    @Override
    public void verifyAndConsume(
            String otpAuthToken,
            Long customerId,
            Long productId,
            Long subscriptionAmount,
            Integer termMonths,
            Long withdrawalAccountId) {
        otpAuthTokenVerifier.verifyAndConsume(new OtpAuthTokenVerification(
                otpAuthToken,
                customerId,
                OtpTransactionType.PRODUCT_SUBSCRIPTION,
                Map.of(
                        "productId", productId,
                        "subscriptionAmount", subscriptionAmount,
                        "termMonths", termMonths,
                        "withdrawalAccountId", withdrawalAccountId)));
    }
}
