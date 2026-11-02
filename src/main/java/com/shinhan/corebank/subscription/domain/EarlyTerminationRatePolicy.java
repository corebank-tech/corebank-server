package com.shinhan.corebank.subscription.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * PH-16: 가입일 포함·해지일 제외 실제 예치기간으로 상품 공통 구간을 선택한다.
 * 가입 당시 확정 금리(appliedRate)는 퍼센트 단위, multiplier는 0~1 사이 비율이다.
 */
public final class EarlyTerminationRatePolicy {

    private final List<Tier> tiers;

    public EarlyTerminationRatePolicy(List<Tier> tiers) {
        if (tiers == null || tiers.isEmpty()) {
            throw new IllegalArgumentException("중도해지이율 구간표가 비어 있습니다.");
        }
        List<Tier> sorted =
                tiers.stream().sorted(Comparator.comparingLong(Tier::minDays)).toList();
        if (sorted.getFirst().minDays() != 0) {
            throw new IllegalArgumentException("중도해지이율 첫 구간은 0일부터 시작해야 합니다.");
        }
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i - 1).minDays() == sorted.get(i).minDays()) {
                throw new IllegalArgumentException("중도해지이율 시작일이 중복됐습니다.");
            }
        }
        this.tiers = List.copyOf(sorted);
    }

    public BigDecimal calculate(LocalDate openedDate, LocalDate terminationDate, BigDecimal appliedRate) {
        if (openedDate == null || terminationDate == null || appliedRate == null) {
            throw new IllegalArgumentException("금리 계산 인자가 비어 있습니다.");
        }
        if (terminationDate.isBefore(openedDate) || appliedRate.signum() < 0) {
            throw new IllegalArgumentException("중도해지 날짜 또는 가입금리가 유효하지 않습니다.");
        }
        long days = ChronoUnit.DAYS.between(openedDate, terminationDate);
        Tier selected = tiers.getFirst();
        for (Tier tier : tiers) {
            if (tier.minDays() > days) {
                break;
            }
            selected = tier;
        }
        return appliedRate.multiply(selected.multiplier());
    }

    public record Tier(long minDays, BigDecimal multiplier) {
        public Tier {
            if (minDays < 0
                    || multiplier == null
                    || multiplier.signum() < 0
                    || multiplier.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("중도해지이율 구간 시작일 또는 적용률이 유효하지 않습니다.");
            }
        }
    }
}
