package com.shinhan.corebank.subscription.application.port.in;

import com.shinhan.corebank.subscription.domain.InterestResult;

public interface InterestCalculationUseCase {

    InterestResult calculate(InterestCalculationCommand command);
}
