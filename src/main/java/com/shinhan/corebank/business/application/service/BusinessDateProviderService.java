package com.shinhan.corebank.business.application.service;

import com.shinhan.corebank.business.api.BusinessDateProvider;
import com.shinhan.corebank.business.application.port.out.BusinessDateQueryPort;
import com.shinhan.corebank.business.domain.BusinessDateType;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BusinessDateProviderService implements BusinessDateProvider {

    private final BusinessDateQueryPort businessDateQueryPort;

    @Override
    public LocalDate today() {
        return businessDateQueryPort
                .findBusinessDate(BusinessDateType.BUSINESS_DATE)
                // Flyway 가 초기 행을 넣으므로 없다면 사용자 잘못이 아니라 데이터 결함이다.
                .orElseThrow(() -> new BusinessException(CommonErrorCode.BUSINESS_DATE_NOT_FOUND));
    }

    @Override
    public LocalDate nextBusinessDay(LocalDate date) {
        LocalDate candidate = date.plusDays(1);
        while (!isBusinessDay(candidate)) {
            candidate = candidate.plusDays(1);
        }
        return candidate;
    }

    @Override
    public boolean isBusinessDay(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return false;
        }
        return !businessDateQueryPort.isHoliday(date);
    }
}
