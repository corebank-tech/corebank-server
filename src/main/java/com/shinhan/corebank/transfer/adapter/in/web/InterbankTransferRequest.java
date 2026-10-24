package com.shinhan.corebank.transfer.adapter.in.web;

import com.shinhan.corebank.transfer.application.port.in.InterbankTransferCommand;
import io.swagger.v3.oas.annotations.media.Schema;

public record InterbankTransferRequest(
        @Schema(description = "출금계좌 ID (내 계좌)", example = "1001") Long withdrawalAccountId,
        @Schema(description = "받는 은행코드. GET /transfers/banks 목록의 bankCode", example = "004") String payeeBankCode,
        @Schema(description = "입금계좌번호. 숫자만 1~16자리, 하이픈 없이", example = "1234567890123") String depositAccountNumber,
        @Schema(description = "이체금액. 1원 이상의 정수", example = "50000", minimum = "1") long amount,
        @Schema(description = "내 통장에 남길 표시내용. 최대 10자", example = "생활비", maxLength = 10) String myPassbookMemo,
        @Schema(description = "상대 통장에 남길 표시내용. 최대 10자. 이모지 등 전문 문자셋에 없는 문자는 거부된다", example = "홍길동", maxLength = 10)
                String recipientPassbookMemo) {

    public InterbankTransferCommand toCommand(Long customerId, String authToken, String otpAuthToken) {
        return InterbankTransferCommand.builder()
                .customerId(customerId)
                .withdrawalAccountId(withdrawalAccountId)
                .payeeBankCode(payeeBankCode)
                .depositAccountNumber(depositAccountNumber)
                .amount(amount)
                .myPassbookMemo(myPassbookMemo)
                .recipientPassbookMemo(recipientPassbookMemo)
                .authToken(authToken)
                .otpAuthToken(otpAuthToken)
                .build();
    }
}
