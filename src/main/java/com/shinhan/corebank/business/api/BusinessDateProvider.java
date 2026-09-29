package com.shinhan.corebank.business.api;

import java.time.LocalDate;

public interface BusinessDateProvider {
    // 현재 영업일. DB에 저장된 값 — 휴일에는 다음 영업일이라 오늘보다 미래일 수 있다 (예: 10/3(토) → 10/6)
    LocalDate today();
    // date 다음 영업일 - 금요일을 넣으면 월요일이 나옴
    LocalDate nextBusinessDay(LocalDate date);

    boolean isBusinessDay(LocalDate date);
}
