package com.shinhan.infoetl.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AsOfWatermarkTest {

    // 2026-10-19 월 ~ 10-23 금, 10-26 월
    private static final LocalDate MON = LocalDate.of(2026, 10, 19);
    private static final LocalDate TUE = LocalDate.of(2026, 10, 20);
    private static final LocalDate WED = LocalDate.of(2026, 10, 21);
    private static final LocalDate THU = LocalDate.of(2026, 10, 22);
    private static final LocalDate FRI = LocalDate.of(2026, 10, 23);
    private static final LocalDate NEXT_MON = LocalDate.of(2026, 10, 26);
    private static final BusinessCalendar NO_HOLIDAYS = new BusinessCalendar(Set.of());

    @Test
    @DisplayName("첫 적재 하루뿐이면 그 날짜가 asOf다")
    void firstLoadOnly() {
        assertThat(AsOfWatermark.compute(Set.of(MON), NO_HOLIDAYS)).isEqualTo(MON);
    }

    @Test
    @DisplayName("영업일이 빠짐없이 이어지면 마지막 날짜가 asOf다")
    void contiguousBusinessDays() {
        assertThat(AsOfWatermark.compute(Set.of(MON, TUE, WED), NO_HOLIDAYS)).isEqualTo(WED);
    }

    @Test
    @DisplayName("주말은 건너뛰고 금요일 다음 월요일로 이어진다")
    void weekendIsNotAGap() {
        assertThat(AsOfWatermark.compute(Set.of(FRI, NEXT_MON), NO_HOLIDAYS)).isEqualTo(NEXT_MON);
    }

    @Test
    @DisplayName("중간 영업일이 비면 그 전날에 머문다")
    void gapStopsTheWatermark() {
        assertThat(AsOfWatermark.compute(Set.of(MON, WED), NO_HOLIDAYS)).isEqualTo(MON);
    }

    @Test
    @DisplayName("빈 날이 공휴일이면 틈이 아니다")
    void holidayGapIsNotAGap() {
        BusinessCalendar calendar = new BusinessCalendar(Set.of(TUE));

        assertThat(AsOfWatermark.compute(Set.of(MON, WED), calendar)).isEqualTo(WED);
    }

    @Test
    @DisplayName("빈 날을 채워 재적재하면 뒤에 적재된 날짜까지 한 번에 따라잡는다")
    void backfillCatchesUpToLaterLoads() {
        assertThat(AsOfWatermark.compute(Set.of(MON, TUE, THU), NO_HOLIDAYS)).isEqualTo(TUE);
        assertThat(AsOfWatermark.compute(Set.of(MON, TUE, WED, THU), NO_HOLIDAYS))
                .isEqualTo(THU);
    }

    @Test
    @DisplayName("완료된 기준일이 없으면 계산할 수 없다")
    void emptyIsRejected() {
        assertThatThrownBy(() -> AsOfWatermark.compute(Set.of(), NO_HOLIDAYS))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
