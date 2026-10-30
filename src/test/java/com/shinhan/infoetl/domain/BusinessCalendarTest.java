package com.shinhan.infoetl.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BusinessCalendarTest {

    // 2026-10-19 월, 10-23 금, 10-24 토, 10-25 일, 10-26 월
    private static final LocalDate MON = LocalDate.of(2026, 10, 19);
    private static final LocalDate FRI = LocalDate.of(2026, 10, 23);
    private static final LocalDate SAT = LocalDate.of(2026, 10, 24);
    private static final LocalDate SUN = LocalDate.of(2026, 10, 25);
    private static final LocalDate NEXT_MON = LocalDate.of(2026, 10, 26);

    @Test
    @DisplayName("평일은 영업일이고 주말과 공휴일은 아니다")
    void weekdaysAreBusinessDaysButWeekendsAndHolidaysAreNot() {
        BusinessCalendar calendar = new BusinessCalendar(Set.of(MON));

        assertThat(calendar.isBusinessDay(MON.plusDays(1))).isTrue();
        assertThat(calendar.isBusinessDay(MON)).isFalse();
        assertThat(calendar.isBusinessDay(SAT)).isFalse();
        assertThat(calendar.isBusinessDay(SUN)).isFalse();
    }

    @Test
    @DisplayName("금요일의 다음 영업일은 월요일이다")
    void nextBusinessDaySkipsWeekend() {
        assertThat(new BusinessCalendar(Set.of()).nextBusinessDay(FRI)).isEqualTo(NEXT_MON);
    }

    @Test
    @DisplayName("다음 날이 공휴일이면 그 다음 영업일을 돌려준다")
    void nextBusinessDaySkipsHoliday() {
        BusinessCalendar calendar = new BusinessCalendar(Set.of(NEXT_MON));

        assertThat(calendar.nextBusinessDay(FRI)).isEqualTo(NEXT_MON.plusDays(1));
    }
}
