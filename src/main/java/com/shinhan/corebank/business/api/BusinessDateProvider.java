package com.shinhan.corebank.business.api;

import java.time.LocalDate;

public interface BusinessDateProvider {
    // 현재 영업일. DB에 저장된 값
    LocalDate today();
    // date 다음 영업일 - 금요일을 넣으면 월요일이 나옴
    LocalDate nextBusinessDay(LocalDate date);

    boolean isBusinessDay(LocalDate date);
}
