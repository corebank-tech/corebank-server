package com.shinhan.corebank.transfer.application.port.in;

public interface FavoriteAccountUpdateUseCase {
    FavoriteAccountResult update(FavoriteAccountUpdateCommand command);
}
