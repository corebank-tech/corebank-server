package com.shinhan.corebank.otp.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerification;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
import com.shinhan.corebank.otp.application.port.out.OtpAuthTokenStorePort;
import com.shinhan.corebank.otp.application.port.out.OtpTransactionDataCanonicalizerPort;
import com.shinhan.corebank.otp.application.port.out.OtpVerificationRequestPort;
import com.shinhan.corebank.otp.domain.exception.OtpErrorCode;
import com.shinhan.corebank.otp.domain.model.OtpAuthTokenPayload;
import com.shinhan.corebank.otp.domain.model.OtpVerificationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// otpAuthToken을 검증한 고객·거래내용과 대조한 뒤 일회용으로 소비한다.
@Service
@RequiredArgsConstructor
public class OtpAuthTokenVerificationService implements OtpAuthTokenVerifier {

    private final OtpAuthTokenStorePort authTokenStorePort;
    private final OtpVerificationRequestPort requestPort;
    private final OtpTransactionDataCanonicalizerPort canonicalizerPort;
    private final OtpTransactionDataValidator transactionDataValidator;

    @Override
    public void verifyAndConsume(OtpAuthTokenVerification verification) {
        if (verification == null
                || verification.otpAuthToken() == null
                || verification.otpAuthToken().isBlank()
                || verification.customerId() == null
                || verification.transactionType() == null) {
            throw new BusinessException(OtpErrorCode.INVALID_AUTH_TOKEN);
        }
        transactionDataValidator.validate(verification.transactionData());

        OtpAuthTokenPayload payload = authTokenStorePort.find(verification.otpAuthToken())
                .orElseThrow(() -> new BusinessException(OtpErrorCode.INVALID_AUTH_TOKEN));
        if (!payload.customerId().equals(verification.customerId())) {
            throw new BusinessException(OtpErrorCode.INVALID_AUTH_TOKEN);
        }

        OtpVerificationRequest request = requestPort.findVerifiedById(payload.otpRequestId())
                .orElseThrow(() -> new BusinessException(OtpErrorCode.INVALID_AUTH_TOKEN));

        if (!request.belongsTo(verification.customerId())) {
            throw new BusinessException(OtpErrorCode.INVALID_AUTH_TOKEN);
        }

        String actualTransactionData = canonicalizerPort.canonicalize(
                verification.transactionData()
        );
        if (request.transactionType() != verification.transactionType()
                || !request.canonicalTransactionData().equals(actualTransactionData)) {
            // 거래정보 불일치는 정상 토큰을 소진하지 않고 OTP0102로 반환한다.
            throw new BusinessException(OtpErrorCode.TRANSACTION_MISMATCH);
        }

        if (!authTokenStorePort.consumeIfMatches(verification.otpAuthToken(), payload)) {
            throw new BusinessException(OtpErrorCode.INVALID_AUTH_TOKEN);
        }
    }
}
