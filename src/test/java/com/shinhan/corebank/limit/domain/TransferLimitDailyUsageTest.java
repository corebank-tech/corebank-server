package com.shinhan.corebank.limit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.common.exception.BusinessException;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TransferLimitDailyUsageTest {

    private static final Long CUSTOMER_ID = 1L;
    private static final LocalDate USAGE_DATE = LocalDate.of(2026, 8, 19);

    @Test
    @DisplayName("음수 금액을 더하려 하면 거부해 누적 사용액이 줄지 않는다")
    void add_negativeAmount_throwsAndKeepsUsedAmount() {
        // given - 사용액이 감소하면 잔여 한도가 부풀려져 한도를 넘긴 이체가 통과한다
        TransferLimitDailyUsage usage = TransferLimitDailyUsage.restore(CUSTOMER_ID, USAGE_DATE, 5_000_000L);

        // when & then
        assertThatThrownBy(() -> usage.add(-3_000_000L))
                .isInstanceOf(BusinessException.class);
        assertThat(usage.getUsedAmount()).isEqualTo(5_000_000L);
    }

    @Test
    @DisplayName("0원은 허용하되 누적 사용액을 바꾸지 않는다")
    void add_zeroAmount_keepsUsedAmount() {
        // given - 0원 이체 차단은 금액 유효성 문제라 TransferCommand 의 책임이다
        TransferLimitDailyUsage usage = TransferLimitDailyUsage.restore(CUSTOMER_ID, USAGE_DATE, 1_000_000L);

        // when
        usage.add(0L);

        // then
        assertThat(usage.getUsedAmount()).isEqualTo(1_000_000L);
    }
}
