package com.shinhan.infoetl.domain;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Set;

// asOf = 첫 적재 기준일부터 영업일마다 COMPLETED가 빠짐없이 있는 마지막 날짜(P3 설계 §5-4).
public final class AsOfWatermark {

    private AsOfWatermark() {}

    public static LocalDate compute(Set<LocalDate> completedBaseDates, BusinessCalendar calendar) {
        if (completedBaseDates.isEmpty()) {
            throw new IllegalArgumentException("완료된 기준일이 하나도 없으면 asOf를 계산할 수 없습니다.");
        }
        LocalDate asOf = Collections.min(completedBaseDates);
        LocalDate next = calendar.nextBusinessDay(asOf);
        while (completedBaseDates.contains(next)) {
            asOf = next;
            next = calendar.nextBusinessDay(asOf);
        }
        return asOf;
    }
}
