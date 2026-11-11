package com.shinhan.corebank.paymentstop.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.paymentstop.domain.exception.PaymentStopErrorCode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("이의제기 처리 기한(D12)")
class ObjectionDeadlineTest {

    // 2026-11-13(금), 2026-11-16(월)... 휴일 하나를 11/18(수)로 둔다
    private static final LocalDate HOLIDAY = LocalDate.of(2026, 11, 18);
    private static final BusinessCalendar WEEKDAYS = calendar(Set.of());
    private static final BusinessCalendar WITH_HOLIDAY = calendar(Set.of(HOLIDAY));

    @Test
    @DisplayName("금요일에 완비를 확인하면 심사 기한은 다음 주 금요일이다")
    void fridayCompletenessEndsNextFriday() {
        assertThat(ObjectionDeadline.reviewDeadline(LocalDate.of(2026, 11, 13), WEEKDAYS))
                .isEqualTo(LocalDate.of(2026, 11, 20));
    }

    @Test
    @DisplayName("기한 안에 휴일이 있으면 하루 밀린다")
    void holidayInsidePushesDeadline() {
        assertThat(ObjectionDeadline.reviewDeadline(LocalDate.of(2026, 11, 13), WITH_HOLIDAY))
                .isEqualTo(LocalDate.of(2026, 11, 23));
    }

    @Test
    @DisplayName("토요일에 확인해도 첫 다음 영업일(월)이 1일째다")
    void weekendStartCountsFromMonday() {
        assertThat(ObjectionDeadline.reviewDeadline(LocalDate.of(2026, 11, 14), WEEKDAYS))
                .isEqualTo(LocalDate.of(2026, 11, 20));
    }

    @Test
    @DisplayName("보완 기한은 1회째 + 5영업일, 2회째 + 3영업일이고 3회째는 PST0006이다")
    void supplementRounds() {
        LocalDate monday = LocalDate.of(2026, 11, 9);

        assertThat(ObjectionDeadline.supplementDue(monday, 1, WEEKDAYS)).isEqualTo(LocalDate.of(2026, 11, 16));
        assertThat(ObjectionDeadline.supplementDue(monday, 2, WEEKDAYS)).isEqualTo(LocalDate.of(2026, 11, 12));
        assertThatThrownBy(() -> ObjectionDeadline.supplementDue(monday, 3, WEEKDAYS))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentStopErrorCode.SUPPLEMENT_LIMIT_EXCEEDED);
    }

    /** 기한 금요일(11/20). 하루 전 영업일(목) 전에는 꺼지고 목요일부터 켜진다. 주말에 걸친 월요일 기한은 금요일부터 켜진다. */
    @ParameterizedTest(name = "오늘 {0}, 기한 {1} → 경보 {2}")
    @CsvSource({
        "2026-11-18, 2026-11-20, false",
        "2026-11-19, 2026-11-20, true",
        "2026-11-20, 2026-11-20, true",
        "2026-11-23, 2026-11-20, true",
        "2026-11-19, 2026-11-23, false",
        "2026-11-20, 2026-11-23, true",
        "2026-11-21, 2026-11-23, true"
    })
    @DisplayName("기한 경보는 오늘이 기한 하루 전 영업일 이후일 때 켜진다(설계 불변식 6)")
    void alertBoundary(LocalDate today, LocalDate deadline, boolean expected) {
        assertThat(ObjectionDeadline.deadlineAlert(
                        ObjectionStatus.UNDER_REVIEW, deadline, WEEKDAYS.nextBusinessDay(today)))
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("확정됐거나 기한이 없으면 경보가 없다")
    void noAlertWhenDecidedOrNoDeadline() {
        LocalDate next = LocalDate.of(2026, 11, 20);
        assertThat(ObjectionDeadline.deadlineAlert(ObjectionStatus.DECIDED, next, next))
                .isFalse();
        assertThat(ObjectionDeadline.deadlineAlert(ObjectionStatus.RECEIVED, null, next))
                .isFalse();
        assertThat(ObjectionDeadline.deadlineAlert(ObjectionStatus.PENDING_APPROVAL, next, next))
                .isTrue();
    }

    @Test
    @DisplayName("보완 기한 경과는 보완 요청 상태에서 기한 다음 날부터다")
    void supplementOverdue() {
        LocalDate due = LocalDate.of(2026, 11, 16);
        assertThat(ObjectionDeadline.supplementOverdue(ObjectionStatus.SUPPLEMENT_REQUESTED, due, due))
                .isFalse();
        assertThat(ObjectionDeadline.supplementOverdue(ObjectionStatus.SUPPLEMENT_REQUESTED, due, due.plusDays(1)))
                .isTrue();
        assertThat(ObjectionDeadline.supplementOverdue(ObjectionStatus.SUPPLEMENT_SUBMITTED, due, due.plusDays(1)))
                .isFalse();
    }

    private static BusinessCalendar calendar(Set<LocalDate> holidays) {
        return date -> {
            LocalDate next = date.plusDays(1);
            while (next.getDayOfWeek() == DayOfWeek.SATURDAY
                    || next.getDayOfWeek() == DayOfWeek.SUNDAY
                    || holidays.contains(next)) {
                next = next.plusDays(1);
            }
            return next;
        };
    }
}
