package com.shinhan.corebank.business.api;

import java.time.LocalDate;

public interface BusinessDateGate {
    // 영업일 행에 공유 락을 잡고 현재 영업일을 읽는다. 락은 트랜잭션 끝까지 유지하고, 같은 트랜잭션의 재호출은 첫 값을 돌려준다
    LocalDate acquireSharedAndReadBusinessDate();
}
