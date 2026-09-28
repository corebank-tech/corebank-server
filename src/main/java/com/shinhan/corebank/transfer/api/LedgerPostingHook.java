package com.shinhan.corebank.transfer.api;

/**
 * 이체 확장점 훅 B — 원장 기표 직후, 같은 트랜잭션 안에서 후속 기표를 붙일 자리다(PH-99).
 *
 * 구현체를 빈으로 등록하기만 하면 {@code TransferExecutionService}가 원장 2행을 저장한 직후 호출한다.
 * 예외를 던지면 원장·잔액이 함께 롤백되고 이체는 ERROR로 확정된다. 예외를 삼키지 않는다.
 *
 * 이벤트가 아니라 동기 호출인 이유: 다른 커밋에 들어가면 "원장은 있는데 전표가 없는" 상태가 정상이 된다.
 */
public interface LedgerPostingHook {

    void afterLedger(LedgerPostingContext context);
}
