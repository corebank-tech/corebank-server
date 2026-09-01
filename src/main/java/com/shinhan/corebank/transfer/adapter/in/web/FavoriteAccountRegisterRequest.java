package com.shinhan.corebank.transfer.adapter.in.web;

import com.shinhan.corebank.transfer.application.port.in.FavoriteAccountRegisterCommand;
import io.swagger.v3.oas.annotations.media.Schema;

public record FavoriteAccountRegisterRequest(
        @Schema(description = "등록할 입금계좌번호 (하이픈 없이)", example = "11012345678901") String depositAccountNumber,
        @Schema(description = "즐겨찾기 별칭", example = "우리 엄마") String alias) {
    public FavoriteAccountRegisterCommand toCommand(Long customerId) {
        return new FavoriteAccountRegisterCommand(customerId, depositAccountNumber, alias);
    }
}
