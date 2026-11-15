package com.shinhan.corebank.paymentstop.domain.gate;

import java.time.LocalDateTime;

/**
 * 서버가 원장에서 읽은 거래 하나. 게이트가 근거로 쓸 수 있는 유일한 사실 등급이다.
 *
 * @param incoming 지급정지 계좌 기준 입금이면 true
 * @param interbank 상대가 타행이면 true
 * @param counterpartyAccountId 당행 상대 계좌 ID. 타행이면 null
 */
public record LedgerFact(
        String transactionNumber,
        boolean incoming,
        boolean interbank,
        Long counterpartyAccountId,
        long amount,
        LocalDateTime occurredAt) {

    public LedgerFact {
        if (transactionNumber == null || transactionNumber.isBlank()) {
            throw new IllegalArgumentException("거래번호는 필수입니다.");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("거래 시각은 필수입니다.");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("거래 금액은 양수여야 합니다.");
        }
        if (interbank == (counterpartyAccountId != null)) {
            throw new IllegalArgumentException("당행 거래만 상대 계좌 ID를 가집니다.");
        }
    }

    public boolean isWithCounterparty(long accountId) {
        return counterpartyAccountId != null && counterpartyAccountId == accountId;
    }
}
