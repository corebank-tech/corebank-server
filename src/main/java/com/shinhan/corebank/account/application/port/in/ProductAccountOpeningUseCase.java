package com.shinhan.corebank.account.application.port.in;

public interface ProductAccountOpeningUseCase {

    AccountOpeningResult open(ProductAccountOpeningCommand command);
}