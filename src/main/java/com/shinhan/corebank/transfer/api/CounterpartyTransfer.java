package com.shinhan.corebank.transfer.api;

import java.time.LocalDateTime;

/**
 * 조회 계좌 기준 이체 한 건.
 *
 * @param direction 조회 계좌 기준 입금(IN)인지 출금(OUT)인지
 * @param counterpartyKind 상대가 당행 계좌인지 타행인지. 타행 입금 경로는 아직 없어 INTERBANK는 출금에만 나온다
 * @param counterpartyAccountId 당행 상대 계좌 ID. 타행이면 null
 * @param occurredAt 원장 기표 시각
 */
public record CounterpartyTransfer(
        String transactionNumber,
        Direction direction,
        CounterpartyKind counterpartyKind,
        Long counterpartyAccountId,
        long amount,
        LocalDateTime occurredAt) {

    public enum Direction {
        IN,
        OUT
    }

    public enum CounterpartyKind {
        INTERNAL,
        INTERBANK
    }
}
