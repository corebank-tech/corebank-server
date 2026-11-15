package com.shinhan.corebank.paymentstop.domain;

import java.time.LocalDate;

/** 영업일 달력. 구현은 business.api 의 BusinessDateProvider 다. 도메인이 다른 도메인을 모르게 함수로만 받는다. */
@FunctionalInterface
public interface BusinessCalendar {

    /** date 다음 영업일. 금요일이면 다음 월요일(휴일이면 그다음)이다. */
    LocalDate nextBusinessDay(LocalDate date);
}
