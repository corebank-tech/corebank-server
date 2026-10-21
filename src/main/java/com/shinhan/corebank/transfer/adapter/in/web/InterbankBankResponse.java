package com.shinhan.corebank.transfer.adapter.in.web;

import com.shinhan.corebank.transfer.application.port.in.InterbankBankQueryUseCase.InterbankBankItem;
import io.swagger.v3.oas.annotations.media.Schema;

public record InterbankBankResponse(
        @Schema(description = "은행코드 3자리. 문자열이라 앞의 0을 지키며 전문 기관코드와 같다", example = "004") String bankCode,
        @Schema(description = "은행명", example = "KB국민은행") String bankName) {

    public static InterbankBankResponse from(InterbankBankItem item) {
        return new InterbankBankResponse(item.bankCode(), item.bankName());
    }
}
