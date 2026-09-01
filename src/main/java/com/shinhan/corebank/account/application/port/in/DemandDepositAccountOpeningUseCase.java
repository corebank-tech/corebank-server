package com.shinhan.corebank.account.application.port.in;

public interface DemandDepositAccountOpeningUseCase {

    AccountOpeningResult open(DemandDepositAccountOpeningCommand command);
}
