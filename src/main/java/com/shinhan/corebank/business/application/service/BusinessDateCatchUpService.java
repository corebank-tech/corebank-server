package com.shinhan.corebank.business.application.service;

import com.shinhan.corebank.business.api.BusinessDateProvider;
import com.shinhan.corebank.business.application.port.in.BusinessDateCatchUpUseCase;
import com.shinhan.corebank.business.application.port.out.BusinessDateCommandPort;
import com.shinhan.corebank.business.domain.BusinessDateType;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// "하루 넘기기"가 아니라 "달력에 맞추기"다 — 매일 넘기면 연휴에 영업일이 앞서 나간다
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessDateCatchUpService implements BusinessDateCatchUpUseCase {

    private final BusinessDateProvider businessDateProvider;
    private final BusinessDateCommandPort businessDateCommandPort;

    @Override
    @Transactional
    public void catchUpTo(LocalDate calendarDate) {
        LocalDate target = businessDateProvider.isBusinessDay(calendarDate)
                ? calendarDate
                : businessDateProvider.nextBusinessDay(calendarDate);
        LocalDate current = businessDateProvider.today();

        if (!current.isBefore(target)) {
            return;
        }
        // false 면 다른 인스턴스가 먼저 같은 목표로 바꾼 것이다
        if (businessDateCommandPort.compareAndSet(BusinessDateType.BUSINESS_DATE, current, target)) {
            log.info("영업일 전환 {} -> {} (달력 {})", current, target, calendarDate);
        }
    }
}
