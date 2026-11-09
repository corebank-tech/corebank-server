package com.shinhan.corebank.fds.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.transfer.api.RecentTransferAggregate;
import com.shinhan.corebank.transfer.api.RecentTransferQuery;
import com.shinhan.corebank.transfer.api.TransferHoldContext;
import com.shinhan.corebank.transfer.api.TransferHoldReleaseMode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class FdsRepeatTransferHoldPolicyTest {
    private final StubTransfers transfers = new StubTransfers();
    private final FdsRepeatTransferHoldPolicy policy = new FdsRepeatTransferHoldPolicy(transfers);
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 9, 12, 0);
    private final TransferHoldContext context = new TransferHoldContext(1, 2, "123456", 1_000_000, now, null);

    @BeforeEach
    void enable() {
        ReflectionTestUtils.setField(policy, "enabled", true);
    }

    @Test
    void disabledPolicyDoesNotReadDatabase() {
        ReflectionTestUtils.setField(policy, "enabled", false);
        assertThat(policy.decide(context).hold()).isFalse();
        assertThat(transfers.calls).isZero();
    }

    @Test
    void thirdTransferAtThreeMillionIsHeldForApproval() {
        transfers.result = new RecentTransferAggregate(2, 2_000_000, now.minusMinutes(9));
        var decision = policy.decide(context);
        assertThat(decision.hold()).isTrue();
        assertThat(decision.policyCode()).isEqualTo("B");
        assertThat(decision.releaseMode()).isEqualTo(TransferHoldReleaseMode.ON_APPROVAL);
        assertThat(policy.order()).isLessThan(200);
        assertThat(transfers.calls).isEqualTo(1);
        assertThat(transfers.since).isEqualTo(now.minusMinutes(10));
    }

    @Test
    void countAndAmountMustBothMeetThreshold() {
        transfers.result = new RecentTransferAggregate(1, 3_000_000, now.minusMinutes(9));
        assertThat(policy.decide(context).hold()).isFalse();
        transfers.result = new RecentTransferAggregate(2, 1_999_999, now.minusMinutes(9));
        assertThat(policy.decide(context).hold()).isFalse();
    }

    @Test
    void queryFailureIsNotTurnedIntoProceed() {
        transfers.fail = true;
        assertThatThrownBy(() -> policy.decide(context)).isInstanceOf(IllegalStateException.class);
    }

    private static final class StubTransfers implements RecentTransferQuery {
        private RecentTransferAggregate result;
        private LocalDateTime since;
        private int calls;
        private boolean fail;

        @Override
        public RecentTransferAggregate aggregateSucceeded(long customerId, String recipient, LocalDateTime since) {
            calls++;
            this.since = since;
            if (fail) throw new IllegalStateException("DB unavailable");
            return result;
        }

        @Override
        public boolean hasSucceededBefore(long customerId, String recipient, LocalDateTime before) {
            throw new AssertionError("정책 B는 신규 수취인을 조회하지 않는다");
        }
    }
}
