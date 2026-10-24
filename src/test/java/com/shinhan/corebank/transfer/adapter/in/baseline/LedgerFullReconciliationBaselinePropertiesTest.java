package com.shinhan.corebank.transfer.adapter.in.baseline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PH-38 전수 대사 베이스라인 측정 설정")
class LedgerFullReconciliationBaselinePropertiesTest {

    @Test
    @DisplayName("예열을 빼고 통계를 낼 회차가 1회 이상이면 만들어진다")
    void acceptsRoundsLeftAfterWarmup() {
        LedgerFullReconciliationBaselineProperties properties = new LedgerFullReconciliationBaselineProperties(4, 1);

        assertThat(properties.measuredRounds()).isEqualTo(3);
    }

    @Test
    @DisplayName("반복 횟수가 0이면 기동 시점에 거부한다")
    void rejectsZeroRounds() {
        assertThatThrownBy(() -> new LedgerFullReconciliationBaselineProperties(0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("예열 회차가 음수면 거부한다")
    void rejectsNegativeWarmup() {
        assertThatThrownBy(() -> new LedgerFullReconciliationBaselineProperties(3, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("예열이 반복 횟수 이상이면 통계를 낼 회차가 없어 거부한다")
    void rejectsWarmupNotLessThanRounds() {
        assertThatThrownBy(() -> new LedgerFullReconciliationBaselineProperties(2, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
