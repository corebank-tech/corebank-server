package com.shinhan.corebank.business.adapter.out.persistence;

import com.shinhan.corebank.business.application.port.out.BusinessDateQueryPort;
import com.shinhan.corebank.business.domain.BusinessDateType;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BusinessDatePersistenceAdapter implements BusinessDateQueryPort {

    private final BusinessDateJpaRepository businessDateRepository;
    private final HolidayJpaRepository holidayRepository;

    @Override
    public Optional<LocalDate> findBusinessDate(BusinessDateType type) {
        return businessDateRepository.findById(type).map(BusinessDateJpaEntity::getBusinessDate);
    }

    @Override
    public boolean isHoliday(LocalDate date) {
        return holidayRepository.existsById(date);
    }
}
