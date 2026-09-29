package com.shinhan.corebank.business.application.port.in;

import java.time.LocalDate;

// 영업일을 달력 날짜에 맞춘다 — 서버 기동 시와 (PH-43 전까지) 매일 자정에 부른다 (#471)
public interface BusinessDateCatchUpUseCase {

    void catchUpTo(LocalDate calendarDate);
}
