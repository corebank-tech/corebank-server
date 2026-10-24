package com.shinhan.infoetl.application.service;

import com.shinhan.infoetl.application.port.in.DailyLoadUseCase;
import com.shinhan.infoetl.application.port.in.RangeLoadUseCase;
import com.shinhan.infoetl.application.port.out.MartLoadPort;
import com.shinhan.infoetl.application.port.out.ReplicaCalendarPort;
import com.shinhan.infoetl.domain.BusinessCalendar;
import com.shinhan.infoetl.domain.FailedDay;
import com.shinhan.infoetl.domain.LoadResult;
import com.shinhan.infoetl.domain.RangeLoadResult;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class RangeLoadService implements RangeLoadUseCase {

    private final DailyLoadUseCase dailyLoad;
    private final ReplicaCalendarPort calendarPort;
    private final MartLoadPort martLoadPort;

    public RangeLoadService(DailyLoadUseCase dailyLoad, ReplicaCalendarPort calendarPort, MartLoadPort martLoadPort) {
        this.dailyLoad = dailyLoad;
        this.calendarPort = calendarPort;
        this.martLoadPort = martLoadPort;
    }

    @Override
    public RangeLoadResult loadRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("--from은 --to보다 늦을 수 없습니다.");
        }
        BusinessCalendar calendar = new BusinessCalendar(calendarPort.readHolidays(from, to));
        List<LoadResult> loaded = new ArrayList<>();
        List<LocalDate> skipped = new ArrayList<>();
        List<FailedDay> failed = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            if (!calendar.isBusinessDay(day)) {
                continue;
            }
            try {
                if (martLoadPort.isCompleted(day)) { // 이미 끝난 날짜는 다시 만들지 않는다(설계 §3-1, 다시 만들려면 --rebuild).
                    skipped.add(day);
                    continue;
                }
                loaded.add(dailyLoad.load(day));
            } catch (RuntimeException e) {
                failed.add(new FailedDay(day, e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }
        return new RangeLoadResult(loaded, skipped, failed);
    }
}
