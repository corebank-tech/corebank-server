package com.shinhan.corebank.business.application.service;

import com.shinhan.corebank.business.api.BusinessDateAdvancer;
import com.shinhan.corebank.business.api.BusinessDateProvider;
import com.shinhan.corebank.business.application.port.out.BusinessDateCommandPort;
import com.shinhan.corebank.business.domain.BusinessDateType;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// business/api 의 전환 계약만 맡는다 — 조회 계약(BusinessDateProviderService)과 빈을 나눈다
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessDateAdvanceService implements BusinessDateAdvancer {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final BusinessDateProvider businessDateProvider;
    private final BusinessDateCommandPort businessDateCommandPort;
    private final Clock clock;

    @Override
    @Transactional
    public LocalDate advanceFrom(LocalDate businessDate) {
        // 아직 오지 않은 영업일은 마감하지 않는다 — 23:30 COB 기준 조건. 자정 후 COB 로 바꾸면 < 로 좁힌다
        if (businessDate.isAfter(LocalDate.now(clock.withZone(SEOUL)))) {
            throw new BusinessException(CommonErrorCode.FUTURE_BUSINESS_DATE_CLOSE);
        }
        LocalDate next = businessDateProvider.nextBusinessDay(businessDate);
        if (businessDateCommandPort.compareAndSet(BusinessDateType.BUSINESS_DATE, businessDate, next)) {
            log.info("영업일 전환 {} -> {} (COB)", businessDate, next);
            return next;
        }
        // 재실행·동시 호출로 이미 넘어간 경우만 통과 — 일반 조회는 옛 스냅샷을 읽으므로 락 읽기로 확인한다
        LocalDate current = businessDateCommandPort
                .findBusinessDateForUpdate(BusinessDateType.BUSINESS_DATE)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.BUSINESS_DATE_NOT_FOUND));
        if (current.equals(next)) {
            return next;
        }
        throw new BusinessException(CommonErrorCode.CONCURRENT_MODIFICATION);
    }
}
