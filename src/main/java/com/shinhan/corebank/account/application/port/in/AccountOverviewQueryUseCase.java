package com.shinhan.corebank.account.application.port.in;

public interface AccountOverviewQueryUseCase {

    AccountOverviewResult getOverview(Long customerId);
}