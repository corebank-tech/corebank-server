package com.shinhan.corebank.subscription.api;

public interface EarlyTerminationUseCase {
    EarlyTerminationResult terminate(EarlyTerminationCommand command);
}
