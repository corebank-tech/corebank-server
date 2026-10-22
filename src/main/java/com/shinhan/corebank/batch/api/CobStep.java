package com.shinhan.corebank.batch.api;

import java.time.LocalDate;

public interface CobStep {
    // 실행 기록·로그에 남는 이름. 영문 소문자+하이픈 권장 (예: "business-date-check")
    String name();
    // 러너가 이 값으로 스텝을 정렬해 순서대로 실행한다. 값이 같은 스텝의 순서는 보장하지 않는다.
    int order();
    // 구현체가 자기 트랜잭션 경계를 연다. 마감 중단은 CobStepHaltException(#556 전까지 실사용 보류, 그 외는 로그만 남기고 계속).
    // 재시작 때 같은 businessDate로 다시 불릴 수 있으니 두 번 실행해도 안전해야 한다.
    void run(LocalDate businessDate);
}
