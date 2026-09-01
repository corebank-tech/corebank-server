package com.shinhan.corebank.autotransfer.adapter.in.web;

import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferChangeCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

public record AutoTransferChangeRequest(
        @Schema(description = "회당 이체금액. 1원 이상의 정수", example = "60000", minimum = "1") Long amount,
        @Schema(description = "이체주기(개월). 1/3/6 중 하나", example = "3") Integer cycleMonths,
        @Schema(description = "이체 종료일. 시작일 이후 ~ 시작일로부터 60개월 이내", example = "2027-09-01") LocalDate endDate,
        @Schema(description = "내 통장에 남길 표시내용. 최대 10자", example = "적금", maxLength = 10) String myPassbookMemo,
        @Schema(description = "상대 통장에 남길 표시내용. 최대 10자", example = "홍길동", maxLength = 10) String recipientPassbookMemo,
        @Schema(description = "변경 불가 항목. null이 아닌 값을 보내면 AUT0003으로 거부됨 - 항상 비워서 보낼 것", nullable = true)
                Long withdrawalAccountId,
        @Schema(description = "변경 불가 항목. null이 아닌 값을 보내면 AUT0003으로 거부됨 - 항상 비워서 보낼 것", nullable = true)
                String depositAccountNumber,
        @Schema(description = "변경 불가 항목. null이 아닌 값을 보내면 AUT0003으로 거부됨 - 항상 비워서 보낼 것", nullable = true)
                Integer transferDay,
        @Schema(description = "계좌 비밀번호 인증 완료 후 발급되는 1회성 인증 토큰") String accountPasswordAuthToken,
        @Schema(description = "OTP 인증 완료 후 발급되는 1회성 인증 토큰") String otpAuthToken) {
    public AutoTransferChangeCommand toCommand(String requestIp, Long customerId) {
        return AutoTransferChangeCommand.builder()
                .customerId(customerId)
                .amount(amount)
                .cycleMonths(cycleMonths)
                .endDate(endDate)
                .myPassbookMemo(myPassbookMemo)
                .recipientPassbookMemo(recipientPassbookMemo)
                .withdrawalAccountId(withdrawalAccountId)
                .depositAccountNumber(depositAccountNumber)
                .transferDay(transferDay)
                .accountPasswordAuthToken(accountPasswordAuthToken)
                .otpAuthToken(otpAuthToken)
                .requestIp(requestIp)
                .build();
    }
}
