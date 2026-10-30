package com.shinhan.corebank.subscription.domain;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AccumulatedDailyBalanceCalculator {

    private AccumulatedDailyBalanceCalculator() {}

    /**
     * 계산 기간 동안 매일의 일말 원장잔액을 합산한다.
     *
     * <p>계산 구간은 [fromInclusive, toExclusive)이다.
     * 같은 날짜에 원장이 여러 건이면 occurredAt이 가장 늦은 원장을 사용하고,
     * occurredAt까지 같으면 ledgerEntryId가 큰 원장을 마지막 원장으로 본다.
     * 거래가 없는 날짜에는 직전 일말잔액을 이월한다.
     *
     * @param openingBalance 계산 시작일 이전 마지막 원장잔액. 이전 원장이 없으면 0
     * @param fromInclusive 계산 시작일, 포함
     * @param toExclusive 계산 종료일, 미포함
     * @param points 계산 기간의 원장 잔액 변화
     * @return 적수(accumulatedDailyBalance)
     */
    public static long calculate(
            long openingBalance, LocalDate fromInclusive, LocalDate toExclusive, List<LedgerBalancePoint> points) {

        validateInput(fromInclusive, toExclusive, points);

        if (fromInclusive.equals(toExclusive)) {
            return 0L;
        }

        Map<LocalDate, LedgerBalancePoint> endOfDayPointByDate = new HashMap<>();

        for (LedgerBalancePoint point : points) {
            if (point == null) {
                throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
            }

            if (point.date().isBefore(fromInclusive) || !point.date().isBefore(toExclusive)) {
                continue;
            }

            LedgerBalancePoint current = endOfDayPointByDate.get(point.date());

            if (current == null || isLater(point, current)) {
                endOfDayPointByDate.put(point.date(), point);
            }
        }

        long currentBalance = openingBalance;
        long accumulatedDailyBalance = 0L;

        for (LocalDate date = fromInclusive; date.isBefore(toExclusive); date = date.plusDays(1)) {

            LedgerBalancePoint endOfDayPoint = endOfDayPointByDate.get(date);

            if (endOfDayPoint != null) {
                currentBalance = endOfDayPoint.balanceAfter();
            }

            accumulatedDailyBalance += currentBalance;
        }

        return accumulatedDailyBalance;
    }

    private static void validateInput(LocalDate fromInclusive, LocalDate toExclusive, List<LedgerBalancePoint> points) {

        if (fromInclusive == null || toExclusive == null || points == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }

        if (fromInclusive.isAfter(toExclusive)) {
            throw new BusinessException(CommonErrorCode.INVALID_DATE_RANGE);
        }
    }

    private static boolean isLater(LedgerBalancePoint candidate, LedgerBalancePoint current) {

        int occurredAtComparison = candidate.occurredAt().compareTo(current.occurredAt());

        if (occurredAtComparison > 0) {
            return true;
        }

        if (occurredAtComparison < 0) {
            return false;
        }

        return candidate.ledgerEntryId() > current.ledgerEntryId();
    }
}
