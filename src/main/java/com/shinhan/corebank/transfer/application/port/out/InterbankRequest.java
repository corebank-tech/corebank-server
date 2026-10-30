package com.shinhan.corebank.transfer.application.port.out;

/**
 * 타행 전문 한 건의 본문. 조회거래도 원거래와 같은 값을 싣는다(전문 규격 §2).
 *
 * @param transactionNumber 원거래 거래번호 — 전문 일련번호가 된다. 수취인 조회는 원거래가 없어 송신 어댑터가 만든 일련번호를 싣는다
 */
public record InterbankRequest(
        String bankCode, String transactionNumber, String accountNumber, long amount, String recipientMemo) {}
