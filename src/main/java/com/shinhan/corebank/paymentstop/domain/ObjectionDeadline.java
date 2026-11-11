package com.shinhan.corebank.paymentstop.domain;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.paymentstop.domain.exception.PaymentStopErrorCode;
import java.time.LocalDate;

/**
 * 이의제기 처리 기한(D12). 심사 기한은 소명 완비 확인일 + 5영업일이고, 보완 기한은 1회째 요청일 + 5영업일, 2회째 + 3영업일이다.
 * 보완은 2회까지다. 5영업일은 법이 아니라 2026-05-04 당국 표준이다(D33).
 *
 * <p>영업일 N일 뒤는 다음 영업일을 N번 구한 날이다. 기준일이 휴일이어도 첫 다음 영업일이 1일째다.
 */
public final class ObjectionDeadline {

    public static final int REVIEW_BUSINESS_DAYS = 5;
    public static final int FIRST_SUPPLEMENT_BUSINESS_DAYS = 5;
    public static final int SECOND_SUPPLEMENT_BUSINESS_DAYS = 3;
    public static final int MAX_SUPPLEMENT_ROUNDS = 2;

    private ObjectionDeadline() {}

    public static LocalDate reviewDeadline(LocalDate confirmedOn, BusinessCalendar calendar) {
        return plusBusinessDays(confirmedOn, REVIEW_BUSINESS_DAYS, calendar);
    }

    /** round 는 이번 보완 요청의 회차(1부터). 3회째부터는 PST0006 이다. */
    public static LocalDate supplementDue(LocalDate requestedOn, int round, BusinessCalendar calendar) {
        return switch (round) {
            case 1 -> plusBusinessDays(requestedOn, FIRST_SUPPLEMENT_BUSINESS_DAYS, calendar);
            case 2 -> plusBusinessDays(requestedOn, SECOND_SUPPLEMENT_BUSINESS_DAYS, calendar);
            default -> throw new BusinessException(PaymentStopErrorCode.SUPPLEMENT_LIMIT_EXCEEDED);
        };
    }

    /**
     * 기한 경보(설계 불변식 6). 확정 전이고 오늘이 심사 기한 하루 전 영업일 이후면 켠다. 「오늘 ≥ 기한 − 1영업일」은 「오늘의 다음 영업일 ≥
     * 기한」과 같아서, 목록 전체에 오늘의 다음 영업일 하나만 구해 쓴다.
     *
     * @param nextBusinessDayAfterToday 오늘의 다음 영업일
     */
    public static boolean deadlineAlert(
            ObjectionStatus status, LocalDate reviewDeadline, LocalDate nextBusinessDayAfterToday) {
        return status != ObjectionStatus.DECIDED
                && reviewDeadline != null
                && !nextBusinessDayAfterToday.isBefore(reviewDeadline);
    }

    /** 보완 기한 경과. 보완 요청 상태에서 오늘이 기한 뒤면 켠다. 자동 확정은 없고 목록에 불수용 대상으로 보인다(D32). */
    public static boolean supplementOverdue(ObjectionStatus status, LocalDate supplementDue, LocalDate today) {
        return status == ObjectionStatus.SUPPLEMENT_REQUESTED && supplementDue != null && today.isAfter(supplementDue);
    }

    private static LocalDate plusBusinessDays(LocalDate from, int days, BusinessCalendar calendar) {
        LocalDate date = from;
        for (int i = 0; i < days; i++) {
            date = calendar.nextBusinessDay(date);
        }
        return date;
    }
}
