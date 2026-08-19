package com.shinhan.corebank.limit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TransferLimitTest {

    private static final Long CUSTOMER_ID = 1L;

    @Test
    @DisplayName("음수 한도로 변경하려 하면 거부해 기존 한도가 유지된다")
    void update_negativeLimit_throwsAndKeepsLimits() {
        // given
        TransferLimit limit = TransferLimit.restore(CUSTOMER_ID, 1_000_000L, 5_000_000L, 0L);

        // when & then
        assertThatThrownBy(() -> limit.update(-1L, 5_000_000L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(limit.getOneTimeLimit()).isEqualTo(1_000_000L);
        assertThat(limit.getDailyLimit()).isEqualTo(5_000_000L);
    }

    @Test
    @DisplayName("0원 한도도 거부한다 - ck_tl_positive 가 0 초과만 허용한다")
    void update_zeroLimit_throws() {
        // given
        TransferLimit limit = TransferLimit.restore(CUSTOMER_ID, 1_000_000L, 5_000_000L, 0L);

        // when & then
        assertThatThrownBy(() -> limit.update(0L, 5_000_000L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
