package com.shinhan.corebank.subscription.adapter.out.persistence;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionTermsAgreementJpaEntityId implements Serializable {
    private Long subscriptionId;
    private Long termsId;
}
