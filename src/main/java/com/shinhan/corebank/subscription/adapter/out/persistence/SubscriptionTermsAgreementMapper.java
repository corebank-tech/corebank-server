package com.shinhan.corebank.subscription.adapter.out.persistence;

import com.shinhan.corebank.subscription.domain.SubscriptionTermsAgreement;
import com.shinhan.corebank.subscription.domain.SubscriptionTermsAgreementId;

final class SubscriptionTermsAgreementMapper {
    private SubscriptionTermsAgreementMapper() {}

    static SubscriptionTermsAgreement toDomain(SubscriptionTermsAgreementJpaEntity entity) {
        return SubscriptionTermsAgreement.builder()
                .id(toDomain(entity.getId()))
                .termsVersion(entity.getTermsVersion())
                .readAt(entity.getReadAt())
                .agreedAt(entity.getAgreedAt())
                .build();
    }

    static SubscriptionTermsAgreementJpaEntity toEntity(SubscriptionTermsAgreement domain) {
        return SubscriptionTermsAgreementJpaEntity.builder()
                .id(toEntity(domain.getId()))
                .termsVersion(domain.getTermsVersion())
                .readAt(domain.getReadAt())
                .agreedAt(domain.getAgreedAt())
                .build();
    }

    static SubscriptionTermsAgreementId toDomain(SubscriptionTermsAgreementJpaEntityId id) {
        return new SubscriptionTermsAgreementId(id.getSubscriptionId(), id.getTermsId());
    }

    static SubscriptionTermsAgreementJpaEntityId toEntity(SubscriptionTermsAgreementId id) {
        return new SubscriptionTermsAgreementJpaEntityId(id.getSubscriptionId(), id.getTermsId());
    }
}
