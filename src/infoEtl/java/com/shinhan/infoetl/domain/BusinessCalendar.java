package com.shinhan.infoetl.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

// 영업일 = 주말도 아니고 공휴일도 아닌 날. holiday 테이블에는 주말이 들어 있지 않다.
public record BusinessCalendar(Set<LocalDate> holidays) {

    public BusinessCalendar {
        holidays = Set.copyOf(holidays);
    }

    public boolean isBusinessDay(LocalDate day) {
        DayOfWeek week = day.getDayOfWeek();
        return week != DayOfWeek.SATURDAY && week != DayOfWeek.SUNDAY && !holidays.contains(day);
    }

    // 주어진 날짜 다음에 오는 첫 영업일. 휴일 정보가 모자라면 틀린 날을 돌려줄 수 있어 호출자가 범위를 책임진다.
    public LocalDate nextBusinessDay(LocalDate day) {
        LocalDate next = day.plusDays(1);
        while (!isBusinessDay(next)) {
            next = next.plusDays(1);
        }
        return next;
    }
}
