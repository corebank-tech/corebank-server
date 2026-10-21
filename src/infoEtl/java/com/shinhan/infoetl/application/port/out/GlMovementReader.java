package com.shinhan.infoetl.application.port.out;

import java.time.LocalDate;

// 마트 트랜잭션 안에서 직전 완료일을 알게 된 뒤 레플리카의 GL 증감을 읽는다.
@FunctionalInterface
public interface GlMovementReader {

    long readDepositMovement(LocalDate fromExclusive, LocalDate toInclusive);
}
