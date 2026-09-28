package com.shinhan.corebank.business.adapter.out.persistence;

import com.shinhan.corebank.business.application.port.out.BusinessDateCommandPort;
import com.shinhan.corebank.business.application.port.out.BusinessDateQueryPort;
import com.shinhan.corebank.business.domain.BusinessDateType;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BusinessDatePersistenceAdapter implements BusinessDateQueryPort, BusinessDateCommandPort {

    private final BusinessDateJpaRepository businessDateRepository;
    private final HolidayJpaRepository holidayRepository;
    private final Clock clock;

    @Override
    public Optional<LocalDate> findBusinessDate(BusinessDateType type) {
        return businessDateRepository.findById(type).map(BusinessDateJpaEntity::getBusinessDate);
    }

    @Override
    public boolean isHoliday(LocalDate date) {
        return holidayRepository.existsById(date);
    }

    @Override
    public boolean compareAndSet(BusinessDateType type, LocalDate expected, LocalDate newDate) {
        return businessDateRepository.compareAndSet(type, expected, newDate, LocalDateTime.now(clock)) == 1;
    }
}
