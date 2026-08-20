package com.shinhan.corebank.subscription.adapter.out.persistence;

import com.shinhan.corebank.subscription.application.port.out.SaveTermsAgreementPort;
import com.shinhan.corebank.subscription.domain.SubscriptionTermsAgreement;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SubscriptionTermsAgreementPersistenceAdapter implements SaveTermsAgreementPort {

    private final SubscriptionTermsAgreementJpaRepository repository;

    @Override
    public void saveAll(List<SubscriptionTermsAgreement> agreements) {
        List<SubscriptionTermsAgreementJpaEntity> entities = agreements.stream()
                .map(SubscriptionTermsAgreementMapper::toEntity)
                .toList();
        repository.saveAll(entities);
    }
}
