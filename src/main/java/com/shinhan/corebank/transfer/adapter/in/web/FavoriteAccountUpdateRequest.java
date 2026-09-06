package com.shinhan.corebank.transfer.adapter.in.web;

import com.shinhan.corebank.transfer.application.port.in.FavoriteAccountUpdateCommand;
import io.swagger.v3.oas.annotations.media.Schema;

public record FavoriteAccountUpdateRequest(@Schema(description = "변경할 즐겨찾기 별칭", example = "우리 엄마") String alias) {
    public FavoriteAccountUpdateCommand toCommand(Long customerId, Long favoriteAccountId) {
        return new FavoriteAccountUpdateCommand(favoriteAccountId, customerId, alias);
    }
}
