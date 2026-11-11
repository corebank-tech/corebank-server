package com.shinhan.corebank.paymentstop.application.service;

import com.shinhan.corebank.business.api.BusinessDateProvider;
import com.shinhan.corebank.paymentstop.application.port.in.ObjectionQueryUseCase.Flags;
import com.shinhan.corebank.paymentstop.domain.BusinessCalendar;
import com.shinhan.corebank.paymentstop.domain.Objection;
import com.shinhan.corebank.paymentstop.domain.ObjectionDeadline;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 이의제기 기한 계산의 진입점. 영업일은 business.api 로 센다(D12). 기준일은 영업일 상태(today())가 아니라 담당자가 요청한 달력 날짜다.
 * 휴일에 확인해도 첫 다음 영업일이 1일째다.
 */
@Component
@RequiredArgsConstructor
class ObjectionCalendar {

    private final BusinessDateProvider businessDateProvider;
    private final Clock clock;

    LocalDate today() {
        return LocalDate.now(clock);
    }

    BusinessCalendar calendar() {
        return businessDateProvider::nextBusinessDay;
    }

    /** 경보와 보완 기한 경과를 계산할 기준. 목록에서는 한 번만 만들어 모든 행에 쓴다. */
    Today snapshot() {
        LocalDate today = today();
        return new Today(today, businessDateProvider.nextBusinessDay(today));
    }

    record Today(LocalDate today, LocalDate nextBusinessDay) {

        Flags flags(Objection objection) {
            return new Flags(
                    ObjectionDeadline.deadlineAlert(
                            objection.getStatus(), objection.getReviewDeadline(), nextBusinessDay),
                    ObjectionDeadline.supplementOverdue(objection.getStatus(), objection.getSupplementDue(), today));
        }
    }
}
