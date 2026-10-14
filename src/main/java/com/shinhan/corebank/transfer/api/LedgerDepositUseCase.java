package com.shinhan.corebank.transfer.api;

/**
 * 예적금 이자·만기·해지 입금 계약(PH-99, #595). 다른 도메인의 예적금 이자·만기·해지 입금은 이 계약으로만 한다.
 *
 * 호출자 트랜잭션에 그대로 참여한다(REQUIRED). 만기·해지는 상태 전이와 입금이 한 트랜잭션이어야
 * 하므로, 호출자가 롤백하면 잔액·원장도 함께 롤백된다. 반대로 이 계약이 거부(예외)하면 호출자 트랜잭션도
 * 롤백 전용이 된다 — 예외를 잡고 같은 트랜잭션에서 다음 건을 이어 갈 수 없으니, 여러 계좌를 도는 배치는
 * 건별로 트랜잭션을 나눈다.
 *
 * 원장(ledger_entry)까지만 기표한다. 전표는 호출자가 같은 트랜잭션에서 {@code gl.api}로 올린다.
 * 같은 건을 두 번 부르면 두 번 기표된다 — 중복은 호출자의 상태 전이(CLOSED 전이·지급 이력)가 막는다.
 */
public interface LedgerDepositUseCase {

    /**
     * 상품 계좌에서 고객 계좌로 옮긴다(원장 2행). 만기해지·중도해지용이다.
     *
     * @param fromAccountId 돈이 빠지는 상품 계좌. MATURITY면 MATURED, TERMINATION이면 ACTIVE여야 한다
     * @param toAccountId   돈이 들어가는 고객 계좌. ACTIVE여야 한다
     * @param type          {@link DepositType#MATURITY} 또는 {@link DepositType#TERMINATION}
     */
    LedgerDepositResult transferBetween(
            long fromAccountId, long toAccountId, long amount, DepositType type, DepositChannel channel);

    /**
     * 은행이 계좌에 돈을 넣는다(원장 1행). 상대편은 호출자가 올리는 GL 전표다. 이자 지급용이다.
     * 고객 거래가 아니므로 계좌의 최종 거래일시(last_transaction_at)는 바꾸지 않는다.
     *
     * @param accountId 돈이 들어가는 계좌. ACTIVE 또는 MATURED여야 한다
     * @param type      {@link DepositType#INTEREST}
     */
    LedgerDepositResult credit(long accountId, long amount, DepositType type, DepositChannel channel);
}
