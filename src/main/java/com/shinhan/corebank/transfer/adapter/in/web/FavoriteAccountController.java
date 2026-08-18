package com.shinhan.corebank.transfer.adapter.in.web;

import java.util.List;

import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.transfer.application.port.in.FavoriteAccountQueryUseCase;
import com.shinhan.corebank.transfer.application.port.in.FavoriteAccountRegisterUseCase;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transfers/favorite-accounts")
@RequiredArgsConstructor
public class FavoriteAccountController {

    private final FavoriteAccountRegisterUseCase registerUseCase;
    private final FavoriteAccountQueryUseCase queryUseCase;
    private final CurrentCustomerProvider currentCustomerProvider;

    @PostMapping
    public ApiResponse<FavoriteAccountResponse> register(@RequestBody FavoriteAccountRegisterRequest request) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        return ApiResponse.success(FavoriteAccountResponse.from(registerUseCase.register(request.toCommand(customerId))));
    }

    @GetMapping
    public ApiResponse<List<FavoriteAccountResponse>> list() {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        return ApiResponse.success(queryUseCase.queryAll(customerId).stream()
                .map(FavoriteAccountResponse::from)
                .toList());
    }
}
