package com.shinhan.corebank.subscription.application.port.out;

import com.shinhan.corebank.subscription.domain.SubscriptionTermsAgreement;

import java.util.List;

public interface SaveTermsAgreementPort {
    void saveAll(List<SubscriptionTermsAgreement> agreements);
}
