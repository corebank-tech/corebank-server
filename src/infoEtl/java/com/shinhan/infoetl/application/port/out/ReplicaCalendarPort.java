package com.shinhan.infoetl.application.port.out;

import java.time.LocalDate;
import java.util.Set;

public interface ReplicaCalendarPort {

    // from~to(양끝 포함) 사이의 공휴일. 주말은 포함하지 않는다.
    Set<LocalDate> readHolidays(LocalDate from, LocalDate to);
}
