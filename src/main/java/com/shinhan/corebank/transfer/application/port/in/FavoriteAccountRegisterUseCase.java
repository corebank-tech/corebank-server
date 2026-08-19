package com.shinhan.corebank.transfer.application.port.in;

public interface FavoriteAccountRegisterUseCase {
    FavoriteAccountResult register(FavoriteAccountRegisterCommand command);
}
