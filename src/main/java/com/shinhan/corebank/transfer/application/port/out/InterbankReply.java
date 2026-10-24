package com.shinhan.corebank.transfer.application.port.out;

/**
 * @param payeeName 상대 은행이 확인한 예금주. APPROVED일 때만 있다
 * @param responseCode 받은 응답코드. 응답이 없었으면 null — 로그·추적용이다
 */
public record InterbankReply(InterbankOutcome outcome, String payeeName, String responseCode) {

    public static InterbankReply of(InterbankOutcome outcome, String responseCode) {
        return new InterbankReply(outcome, null, responseCode);
    }
}
