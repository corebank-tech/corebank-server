package com.shinhan.corebank.transfer.api;

import java.time.LocalDateTime;

/**
 * 조회 계좌 기준 이체 한 건.
 *
 * @param direction 조회 계좌 기준 입금(IN)인지 출금(OUT)인지
 * @param counterpartyKind 상대가 당행 계좌인지 타행인지. 타행 입금 경로는 아직 없어 INTERBANK는 출금에만 나온다
 * @param counterpartyAccountId 당행 상대 계좌 ID. 타행이면 null
 * @param occurredAt 원장 기표 시각
 * @param settled 결과가 확정됐는지. false면 처리 불명(TIMEOUT) 타행 출금이다. 출금은 이미 원장에 반영됐고 조회거래가 성공이나 실패로
 *     확정한다. 실패로 확정되면 출금을 되돌리고 이 조회에서 빠진다
 */
public record CounterpartyTransfer(
        String transactionNumber,
        Direction direction,
        CounterpartyKind counterpartyKind,
        Long counterpartyAccountId,
        long amount,
        LocalDateTime occurredAt,
        boolean settled) {

    public enum Direction {
        IN,
        OUT
    }

    public enum CounterpartyKind {
        INTERNAL,
        INTERBANK
    }
}
