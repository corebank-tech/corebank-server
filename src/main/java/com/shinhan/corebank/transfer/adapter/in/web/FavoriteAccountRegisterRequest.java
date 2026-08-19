package com.shinhan.corebank.transfer.adapter.in.web;

import com.shinhan.corebank.transfer.application.port.in.FavoriteAccountRegisterCommand;

public record FavoriteAccountRegisterRequest(String depositAccountNumber, String alias) {
    public FavoriteAccountRegisterCommand toCommand(Long customerId) {
        return new FavoriteAccountRegisterCommand(customerId, depositAccountNumber, alias);
    }
}
