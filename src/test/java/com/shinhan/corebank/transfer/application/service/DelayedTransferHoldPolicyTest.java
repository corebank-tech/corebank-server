package com.shinhan.corebank.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.shinhan.corebank.transfer.api.TransferHoldContext;
import com.shinhan.corebank.transfer.api.TransferHoldDecision;
import com.shinhan.corebank.transfer.api.TransferHoldReleaseMode;
import com.shinhan.corebank.transfer.application.port.out.DelayedTransferSettingPort;
import com.shinhan.corebank.transfer.config.DelayedTransferProperties;
import com.shinhan.corebank.transfer.domain.DelayedTransferSetting;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("훅 C 지연이체 구현체")
class DelayedTransferHoldPolicyTest {

    private static final LocalDateTime REQUESTED_AT = LocalDateTime.of(2031, 7, 1, 10, 0);

    private final DelayedTransferSettingPort port = mock(DelayedTransferSettingPort.class);

    @Test
    @DisplayName("전역 스위치가 꺼져 있으면 고객 설정을 읽지 않고 바로 실행한다")
    void switchOff_doesNotReadSetting() {
        TransferHoldDecision decision = policy(false).decide(context(5_000_000L));

        assertThat(decision.hold()).isFalse();
        verifyNoInteractions(port);
    }

    @Test
    @DisplayName("설정이 없거나 꺼져 있으면 바로 실행한다")
    void noSettingOrDisabled_proceeds() {
        given(port.findByCustomerId(1L)).willReturn(Optional.empty());
        assertThat(policy(true).decide(context(5_000_000L)).hold()).isFalse();

        given(port.findByCustomerId(1L)).willReturn(Optional.of(DelayedTransferSetting.of(1L, false, 1L, 3)));
        assertThat(policy(true).decide(context(5_000_000L)).hold()).isFalse();
    }

    @Test
    @DisplayName("기준금액 미만은 바로 실행하고, 기준금액과 같거나 크면 요청 시각 + 지연시간까지 보류한다")
    void threshold_isInclusive() {
        given(port.findByCustomerId(1L)).willReturn(Optional.of(DelayedTransferSetting.of(1L, true, 1_000_000L, 5)));

        assertThat(policy(true).decide(context(999_999L)).hold()).isFalse();

        TransferHoldDecision decision = policy(true).decide(context(1_000_000L));
        assertThat(decision.hold()).isTrue();
        assertThat(decision.policyCode()).isEqualTo(DelayedTransferSetting.POLICY_CODE);
        assertThat(decision.releaseMode()).isEqualTo(TransferHoldReleaseMode.AT_TIME);
        assertThat(decision.releaseAt()).isEqualTo(REQUESTED_AT.plusHours(5));
    }

    @Test
    @DisplayName("승인으로 푸는 정책(FDS)이 먼저 정하도록 order가 뒤에 있다")
    void order_isAfterApprovalPolicies() {
        assertThat(policy(true).order()).isEqualTo(200);
    }

    private DelayedTransferHoldPolicy policy(boolean enabled) {
        return new DelayedTransferHoldPolicy(new DelayedTransferProperties(enabled, Duration.ofMinutes(5), 100), port);
    }

    private static TransferHoldContext context(long amount) {
        return new TransferHoldContext(1L, 101L, "110222222222", amount, REQUESTED_AT, null);
    }
}
