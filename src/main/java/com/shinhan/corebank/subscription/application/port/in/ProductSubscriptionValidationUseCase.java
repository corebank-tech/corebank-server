package com.shinhan.corebank.subscription.application.port.in;

import com.shinhan.corebank.subscription.domain.SubscriptionValidation;

public interface ProductSubscriptionValidationUseCase {
    SubscriptionValidation validate(ProductSubscriptionValidationCommand command);
}
