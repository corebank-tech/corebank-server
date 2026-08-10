package com.shinhan.corebank.subscription.domain;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
@AllArgsConstructor
public class SubscriptionTermsAgreementId {
    private Long subscriptionId;
    private Long termsId;
}
