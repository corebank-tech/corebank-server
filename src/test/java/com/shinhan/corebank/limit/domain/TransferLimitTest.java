package com.shinhan.corebank.limit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TransferLimitTest {

    private static final Long CUSTOMER_ID = 1L;

    @Test
    @DisplayName("1회 한도를 0 이하로 변경하려 하면 거부해 기존 한도가 유지된다")
    void update_nonPositiveOneTimeLimit_throwsAndKeepsLimits() {
        // given
        TransferLimit limit = TransferLimit.restore(CUSTOMER_ID, 1_000_000L, 5_000_000L);

        // when & then
        assertThatThrownBy(() -> limit.update(-1L, 5_000_000L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_INPUT);
        assertThatThrownBy(() -> limit.update(0L, 5_000_000L))
                .isInstanceOf(BusinessException.class);

        assertThat(limit.getOneTimeLimit()).isEqualTo(1_000_000L);
        assertThat(limit.getDailyLimit()).isEqualTo(5_000_000L);
    }

    @Test
    @DisplayName("1일 한도를 0 이하로 변경하려 해도 같은 기준으로 거부한다")
    void update_nonPositiveDailyLimit_throws() {
        // given - transfer_limit 의 ck_tl_positive 가 두 컬럼 모두 0 초과만 허용한다
        TransferLimit limit = TransferLimit.restore(CUSTOMER_ID, 1_000_000L, 5_000_000L);

        // when & then
        assertThatThrownBy(() -> limit.update(1_000_000L, -1L))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> limit.update(1_000_000L, 0L))
                .isInstanceOf(BusinessException.class);
    }
}
