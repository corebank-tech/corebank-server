package com.shinhan.corebank.transfer.api;

/**
 * 이체 확장점 훅 B — 원장 기표 직후, 같은 트랜잭션 안에서 후속 기표를 붙일 자리다(PH-99).
 *
 * 구현체를 빈으로 등록하기만 하면 원장 2행을 저장한 직후 호출된다 — {@code TransferExecutionService}(이체),
 * {@code ProductSubscriptionDepositService}(상품가입 초입금), {@code TransferCorrectionService}(정정 체인).
 * 예외를 던지면 원장·잔액이 함께 롤백되고 이체는 ERROR로 확정된다. 예외를 삼키지 않는다.
 * 롤백되는 건 이 트랜잭션의 DB 쓰기뿐이다. 외부 호출은 여기서 하지 말고 아웃박스로 넘긴다.
 *
 * 이벤트가 아니라 동기 호출인 이유: 다른 커밋에 들어가면 "원장은 있는데 전표가 없는" 상태가 정상이 된다.
 */
public interface LedgerPostingHook {

    void afterLedger(LedgerPostingContext context);
}
