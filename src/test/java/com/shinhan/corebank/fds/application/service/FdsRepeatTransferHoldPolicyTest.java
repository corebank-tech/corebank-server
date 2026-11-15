package com.shinhan.corebank.fds.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.transfer.api.RecentSuccessfulTransfer;
import com.shinhan.corebank.transfer.api.RecentTransferAggregate;
import com.shinhan.corebank.transfer.api.RecentTransferQuery;
import com.shinhan.corebank.transfer.api.TransferHoldContext;
import com.shinhan.corebank.transfer.api.TransferHoldReleaseMode;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class FdsRepeatTransferHoldPolicyTest {
    private final StubTransfers transfers = new StubTransfers();
    private final BehaviorHesitationService hesitation = mock(BehaviorHesitationService.class);
    private final FdsRepeatTransferHoldPolicy policy = new FdsRepeatTransferHoldPolicy(transfers, hesitation);
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 9, 12, 0);
    private final TransferHoldContext context = new TransferHoldContext(1, 2, "123456", 1_000_000, now, null);

    @BeforeEach
    void enable() {
        ReflectionTestUtils.setField(policy, "enabled", true);
    }

    @Test
    void disabledPolicyDoesNotReadAnyFdsData() {
        ReflectionTestUtils.setField(policy, "enabled", false);

        assertThat(policy.decide(context).hold()).isFalse();

        assertThat(transfers.findCalls).isZero();
        assertThat(transfers.beforeCalls).isZero();
        verifyNoInteractions(hesitation);
    }

    @Test
    void policyAHoldsBeforePolicyBWhenBothMatch() {
        transfers.result = previous(1_000_000, 1_000_000);
        when(hesitation.anyHesitation(1, null, List.of(11L, 12L))).thenReturn(true);

        var decision = policy.decide(context);

        assertThat(decision.hold()).isTrue();
        assertThat(decision.policyCode()).isEqualTo("A");
        assertThat(decision.releaseMode()).isEqualTo(TransferHoldReleaseMode.ON_APPROVAL);
        assertThat(decision.evidence().count10m()).isEqualTo(3);
        assertThat(decision.evidence().amount10m()).isEqualTo(3_000_000);
        assertThat(decision.evidence().hesitationInWindow()).isTrue();
        assertThat(decision.evidence().newRecipientAtWindowStart()).isTrue();
        assertThat(transfers.before).isEqualTo(now.minusMinutes(9));
    }

    @Test
    void policyBHoldsAtThreeMillionWithoutHesitation() {
        transfers.result = previous(1_000_000, 1_000_000);

        var decision = policy.decide(context);

        assertThat(decision.hold()).isTrue();
        assertThat(decision.policyCode()).isEqualTo("B");
        assertThat(decision.evidence().count10m()).isEqualTo(3);
        assertThat(decision.evidence().amount10m()).isEqualTo(3_000_000);
        assertThat(policy.order()).isLessThan(200);
        assertThat(transfers.findCalls).isEqualTo(1);
        assertThat(transfers.since).isEqualTo(now.minusMinutes(10));
    }

    @Test
    void policyAHoldsAtOneAndHalfMillionWithCurrentHesitation() {
        transfers.result = previous(500_000, 500_000);
        TransferHoldContext withFlow = new TransferHoldContext(1, 2, "123456", 500_000, now, "flow-current");
        when(hesitation.anyHesitation(1, "flow-current", List.of(11L, 12L))).thenReturn(true);

        assertThat(policy.decide(withFlow).policyCode()).isEqualTo("A");
        assertThat(transfers.before).isEqualTo(now.minusMinutes(9));
    }

    @Test
    void existingRecipientPreventsPolicyAAndFallsThroughToProceed() {
        transfers.result = previous(500_000, 500_000);
        transfers.hasBefore = true;
        when(hesitation.anyHesitation(1, null, List.of(11L, 12L))).thenReturn(true);

        assertThat(policy.decide(new TransferHoldContext(1, 2, "123456", 500_000, now, null))
                        .hold())
                .isFalse();
    }

    @Test
    void countAndAmountMustBothMeetThresholdAndAvoidUnneededBehaviorReads() {
        transfers.result = List.of(new RecentSuccessfulTransfer(11, 3_000_000, now.minusMinutes(9)));
        assertThat(policy.decide(context).hold()).isFalse();

        verify(hesitation, never()).anyHesitation(anyLong(), any(), anyList());
    }

    @Test
    void threeTransfersOneWonBelowPolicyBThresholdProceedWithoutHesitation() {
        transfers.result = previous(1_000_000, 999_999);

        assertThat(policy.decide(context).hold()).isFalse();
    }

    @Test
    void amountOverflowAndQueryFailureAreNotTurnedIntoProceed() {
        transfers.result = previous(Long.MAX_VALUE, 1);
        assertThatThrownBy(() -> policy.decide(context)).isInstanceOf(ArithmeticException.class);

        transfers.fail = true;
        assertThatThrownBy(() -> policy.decide(context)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void behaviorStorageFailureIsNotTurnedIntoPolicyB() {
        transfers.result = previous(1_000_000, 1_000_000);
        when(hesitation.anyHesitation(1, null, List.of(11L, 12L)))
                .thenThrow(new IllegalStateException("behavior DB unavailable"));

        assertThatThrownBy(() -> policy.decide(context)).isInstanceOf(IllegalStateException.class);
    }

    private List<RecentSuccessfulTransfer> previous(long first, long second) {
        return List.of(
                new RecentSuccessfulTransfer(11, first, now.minusMinutes(9)),
                new RecentSuccessfulTransfer(12, second, now.minusMinutes(8)));
    }

    private static final class StubTransfers implements RecentTransferQuery {
        private List<RecentSuccessfulTransfer> result = List.of();
        private LocalDateTime since;
        private LocalDateTime before;
        private int findCalls;
        private int beforeCalls;
        private boolean fail;
        private boolean hasBefore;

        @Override
        public RecentTransferAggregate aggregateSucceeded(long customerId, String recipient, LocalDateTime since) {
            throw new AssertionError("정책은 목록 한 번으로 집계와 과거 행동 연결을 함께 계산한다");
        }

        @Override
        public List<RecentSuccessfulTransfer> findSucceeded(long customerId, String recipient, LocalDateTime since) {
            findCalls++;
            this.since = since;
            if (fail) throw new IllegalStateException("DB unavailable");
            return result;
        }

        @Override
        public boolean hasSucceededBefore(long customerId, String recipient, LocalDateTime before) {
            beforeCalls++;
            this.before = before;
            return hasBefore;
        }
    }
}
