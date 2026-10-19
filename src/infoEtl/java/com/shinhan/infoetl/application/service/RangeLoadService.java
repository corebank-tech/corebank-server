package com.shinhan.infoetl.application.service;

import com.shinhan.infoetl.application.port.in.DailyLoadUseCase;
import com.shinhan.infoetl.application.port.in.RangeLoadUseCase;
import com.shinhan.infoetl.application.port.out.ReplicaCalendarPort;
import com.shinhan.infoetl.domain.FailedDay;
import com.shinhan.infoetl.domain.LoadResult;
import com.shinhan.infoetl.domain.RangeLoadResult;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class RangeLoadService implements RangeLoadUseCase {

    private final DailyLoadUseCase dailyLoad;
    private final ReplicaCalendarPort calendarPort;

    public RangeLoadService(DailyLoadUseCase dailyLoad, ReplicaCalendarPort calendarPort) {
        this.dailyLoad = dailyLoad;
        this.calendarPort = calendarPort;
    }

    @Override
    public RangeLoadResult loadRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("--from은 --to보다 늦을 수 없습니다.");
        }
        Set<LocalDate> holidays = calendarPort.readHolidays(from, to);
        List<LoadResult> loaded = new ArrayList<>();
        List<FailedDay> failed = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            if (!isBusinessDay(day, holidays)) {
                continue;
            }
            try {
                loaded.add(dailyLoad.load(day));
            } catch (RuntimeException e) {
                failed.add(new FailedDay(day, e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }
        return new RangeLoadResult(loaded, failed);
    }

    // 영업일 = 주말도 아니고 공휴일도 아닌 날(holiday 테이블은 주말을 넣지 않는다).
    private static boolean isBusinessDay(LocalDate day, Set<LocalDate> holidays) {
        DayOfWeek week = day.getDayOfWeek();
        return week != DayOfWeek.SATURDAY && week != DayOfWeek.SUNDAY && !holidays.contains(day);
    }
}
