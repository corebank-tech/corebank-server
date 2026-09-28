package com.shinhan.corebank.business.application.port.in;

import java.time.LocalDate;

// 영업일을 달력 날짜에 맞춘다 — PH-43 COB 도입 전까지 쓰는 임시 경로 (#471)
public interface BusinessDateCatchUpUseCase {

    void catchUpTo(LocalDate calendarDate);
}
