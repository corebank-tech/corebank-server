package com.shinhan.infoetl.domain;

import java.time.LocalDate;
import java.util.Set;

// asOf = 첫 적재 기준일부터 영업일마다 COMPLETED가 빠짐없이 있는 마지막 날짜(P3 설계 §5-4). 첫 적재일이 COMPLETED가 아니면 null.
public final class AsOfWatermark {

    private AsOfWatermark() {}

    public static LocalDate compute(
            LocalDate firstLoadDate, Set<LocalDate> completedBaseDates, BusinessCalendar calendar) {
        if (!completedBaseDates.contains(firstLoadDate)) {
            return null;
        }
        LocalDate asOf = firstLoadDate;
        LocalDate next = calendar.nextBusinessDay(asOf);
        while (completedBaseDates.contains(next)) {
            asOf = next;
            next = calendar.nextBusinessDay(asOf);
        }
        return asOf;
    }
}
