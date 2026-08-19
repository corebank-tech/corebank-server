package com.shinhan.corebank.scheduledtransfer.adapter.in.web;

import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferRegisterCommand;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public record ScheduledTransferRegisterRequest(
        @Schema(description = "출금계좌 ID (내 계좌)", example = "1001")
        Long withdrawalAccountId,
        @Schema(description = "입금계좌번호 (하이픈 없이, 당행 전용)", example = "11012345678901")
        String depositAccountNumber,
        @Schema(description = "예금주명", example = "홍길동")
        String payeeName,
        @Schema(description = "이체금액. 1원 이상의 정수", example = "50000", minimum = "1")
        Long amount,
        @Schema(description = "예약 실행일. 익일부터 1년(365일) 이내", example = "2026-09-01")
        LocalDate scheduledDate,
        @Schema(description = "내 통장에 남길 표시내용. 최대 10자", example = "생활비", maxLength = 10)
        String myPassbookMemo,
        @Schema(description = "상대 통장에 남길 표시내용. 최대 10자", example = "홍길동", maxLength = 10)
        String recipientPassbookMemo,
        @Schema(description = "계좌 비밀번호 인증 완료 후 발급되는 1회성 인증 토큰")
        String accountPasswordAuthToken,
        @Schema(description = "OTP 인증 완료 후 발급되는 1회성 인증 토큰")
        String otpAuthToken) {
    public ScheduledTransferRegisterCommand toCommand(String requestIp, Long customerId) {
        return ScheduledTransferRegisterCommand.builder()
                .customerId(customerId)
                .withdrawalAccountId(withdrawalAccountId)
                .depositAccountNumber(depositAccountNumber)
                .payeeName(payeeName)
                .amount(amount)
                .scheduledDate(scheduledDate)
                .myPassbookMemo(myPassbookMemo)
                .recipientPassbookMemo(recipientPassbookMemo)
                .accountPasswordAuthToken(accountPasswordAuthToken)
                .otpAuthToken(otpAuthToken)
                .requestIp(requestIp)
                .build();
    }
}
