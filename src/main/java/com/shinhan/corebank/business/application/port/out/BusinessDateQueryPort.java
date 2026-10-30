package com.shinhan.corebank.business.application.port.out;

import com.shinhan.corebank.business.domain.BusinessDateType;
import java.time.LocalDate;
import java.util.Optional;

public interface BusinessDateQueryPort {
    Optional<LocalDate> findBusinessDate(BusinessDateType type);

    boolean isHoliday(LocalDate date);
}
