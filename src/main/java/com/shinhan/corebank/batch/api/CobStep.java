package com.shinhan.corebank.batch.api;

import java.time.LocalDate;

public interface CobStep {
    // 실행 기록·로그에 남는 이름. 영문 소문자+하이픈 권장 (예: "business-date-check")
    String name();
    // 러너가 이 값으로 스텝을 정렬해 순서대로 실행한다. 값이 같은 스텝의 순서는 보장하지 않는다.
    int order();
    // 구현체가 자기 트랜잭션 경계를 연다 - 이 인터페이스는 트랜잭션을 관리하지 않는다.
    void run(LocalDate businessDate);
}
