package com.shinhan.corebank.subscription.domain;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class SubscriptionTermsAgreementId {
    private final Long subscriptionId;
    private final Long termsId;

    public SubscriptionTermsAgreementId(Long subscriptionId, Long termsId) {
        if (subscriptionId == null || termsId == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
        this.subscriptionId = subscriptionId;
        this.termsId = termsId;
    }
}
