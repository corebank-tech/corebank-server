package com.shinhan.corebank.transfer.api;

/**
 * 예적금 이자·만기·해지 입금 계약(PH-99, #595). 다른 도메인은 이 계약으로만 계좌에 돈을 넣는다.
 *
 * 호출자 트랜잭션에 그대로 참여한다(REQUIRED). 만기·해지는 상태 전이와 입금이 한 트랜잭션이어야
 * 하므로, 호출자가 롤백하면 잔액·원장도 함께 롤백된다.
 *
 * 원장(ledger_entry)까지만 기표한다. 전표는 호출자가 같은 트랜잭션에서 {@code gl.api}로 올린다.
 * 같은 건을 두 번 부르면 두 번 기표된다 — 중복은 호출자의 상태 전이(CLOSED 전이·지급 이력)가 막는다.
 */
public interface LedgerDepositUseCase {

    /**
     * 상품 계좌에서 고객 계좌로 옮긴다(원장 2행). 만기해지·중도해지용이다.
     *
     * @param fromAccountId 돈이 빠지는 상품 계좌. ACTIVE 또는 MATURED여야 한다
     * @param toAccountId   돈이 들어가는 고객 계좌. ACTIVE여야 한다
     * @param type          {@link DepositType#MATURITY} 또는 {@link DepositType#TERMINATION}
     */
    LedgerDepositResult transferBetween(
            long fromAccountId, long toAccountId, long amount, DepositType type, DepositChannel channel);

    /**
     * 은행이 계좌에 돈을 넣는다(원장 1행). 상대편은 호출자가 올리는 GL 전표다. 이자 지급용이다.
     *
     * @param accountId 돈이 들어가는 계좌. ACTIVE 또는 MATURED여야 한다
     * @param type      {@link DepositType#INTEREST}
     */
    LedgerDepositResult credit(long accountId, long amount, DepositType type, DepositChannel channel);
}
