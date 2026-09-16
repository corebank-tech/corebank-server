package com.shinhan.corebank.subscription.domain;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
public class SubscriptionTermsAgreement {
    private final SubscriptionTermsAgreementId id;
    private final String termsVersion;
    private final LocalDateTime readAt;
    private final LocalDateTime agreedAt;

    @Builder
    public SubscriptionTermsAgreement(
            SubscriptionTermsAgreementId id, String termsVersion, LocalDateTime readAt, LocalDateTime agreedAt) {
        if (id == null || termsVersion == null || agreedAt == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }

        this.id = id;
        this.termsVersion = termsVersion;
        this.readAt = readAt;
        this.agreedAt = agreedAt;
    }
}
