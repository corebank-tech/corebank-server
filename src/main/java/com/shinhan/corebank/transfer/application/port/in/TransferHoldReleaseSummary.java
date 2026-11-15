package com.shinhan.corebank.transfer.application.port.in;

/**
 * 해제 한 회차의 결과.
 *
 * @param resumed 멈춘 실행을 마무리한 건수
 * @param released 이번에 실행한 건수(이체 성공·실패 모두)
 * @param expired  보류한 날이 지나 EXPIRED로 닫은 승인 대기 건수(#998)
 * @param skipped  꺼낸 뒤 다른 곳이 먼저 처리했거나 예외로 건너뛴 건수
 */
public record TransferHoldReleaseSummary(int resumed, int released, int expired, int skipped) {}
