package com.shinhan.corebank.account.adapter.in.web;

import com.shinhan.corebank.account.application.port.in.AccountOverviewQueryUseCase;
import com.shinhan.corebank.account.application.port.in.AccountOverviewResult;
import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountOverviewQueryUseCase accountOverviewQueryUseCase;
    private final CurrentCustomerProvider currentCustomerProvider;

    @GetMapping
    public ApiResponse<AccountOverviewResponse> getAccounts() {
        Long customerId =
                currentCustomerProvider.getCurrentCustomerId();

        AccountOverviewResult result =
                accountOverviewQueryUseCase
                        .getOverview(customerId);

        return ApiResponse.success(
                AccountOverviewResponse.from(result)
        );
    }
}