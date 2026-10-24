package com.shinhan.corebank.subscription.api;

/**
 * PH-15 예적금 만기해지 공개 계약.
 *
 * <p>MATURED 계좌의 이자 지급, 원리금 이체, CLOSED 전이를 하나의 트랜잭션에서 수행한다.
 *
 * <p>호출자는 계좌 소유권과 만기 조건을 서버 측 데이터로 검증한다.
 * BusinessDateGate는 계좌 잠금보다 먼저 획득한다.
 *
 * <p>해지 출금 결과의 tradeDate를 account.closed_trade_date에 저장한다.
 * 이자 입금 거래일이나 시스템 날짜로 대체하지 않는다.
 */
public interface MaturityTerminationUseCase {

    MaturityTerminationResult terminate(MaturityTerminationCommand command);
}
