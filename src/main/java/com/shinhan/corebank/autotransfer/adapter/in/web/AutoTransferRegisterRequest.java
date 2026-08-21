package com.shinhan.corebank.autotransfer.adapter.in.web;

import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferRegisterCommand;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public record AutoTransferRegisterRequest (
        @Schema(description = "출금계좌 ID (내 계좌)", example = "1001")
        Long withdrawalAccountId,
        @Schema(description = "입금계좌번호 (하이픈 없이)", example = "110123456789")
        String depositAccountNumber,
        @Schema(description = "예금주명", example = "홍길동")
        String payeeName,
        @Schema(description = "회당 이체금액. 1원 이상의 정수", example = "50000", minimum = "1")
        Long amount,
        @Schema(description = "이체주기(개월). 1/3/6 중 하나", example = "1")
        Integer cycleMonths,
        @Schema(description = "매 이체주기 내 이체지정일. 1~31", example = "25", minimum = "1", maximum = "31")
        Integer transferDay,
        @Schema(description = "이체 시작일. 익일부터 1년 이내", example = "2026-09-01")
        LocalDate startDate,
        @Schema(description = "이체 종료일. 시작일 이후 ~ 시작일로부터 60개월 이내", example = "2027-09-01")
        LocalDate endDate,
        @Schema(description = "내 통장에 남길 표시내용. 최대 10자", example = "적금", maxLength = 10)
        String myPassbookMemo,
        @Schema(description = "상대 통장에 남길 표시내용. 최대 10자", example = "홍길동", maxLength = 10)
        String recipientPassbookMemo,
        @Schema(description = "계좌 비밀번호 인증 완료 후 발급되는 1회성 인증 토큰")
        String accountPasswordAuthToken  ){
    public AutoTransferRegisterCommand toCommand(String requestIp, Long customerId) {
        return AutoTransferRegisterCommand.builder()
                .customerId(customerId)
                .withdrawalAccountId(withdrawalAccountId)
                .depositAccountNumber(depositAccountNumber)
                .payeeName(payeeName)
                .amount(amount)
                .cycleMonths(cycleMonths)
                .transferDay(transferDay)
                .startDate(startDate)
                .endDate(endDate)
                .myPassbookMemo(myPassbookMemo)
                .recipientPassbookMemo(recipientPassbookMemo)
                .accountPasswordAuthToken(accountPasswordAuthToken)
                .requestIp(requestIp)
                .build();
    }
}
