package com.shinhan.corebank.gl.api;

/**
 * 분개 한 줄 요청.
 *
 * @param accountCode 계정과목 코드(5자리)
 * @param drCr        차변·대변
 * @param amount      금액(원). 항상 양수
 */
public record JournalLine(String accountCode, JournalDirection drCr, long amount) {}
