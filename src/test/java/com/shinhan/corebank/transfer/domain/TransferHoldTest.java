package com.shinhan.corebank.transfer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.transfer.api.TransferHoldDecision;
import com.shinhan.corebank.transfer.api.TransferHoldReleaseMode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("이체 보류 도메인")
class TransferHoldTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2031, 7, 1, 10, 0);

    @Test
    @DisplayName("보류 판정으로 만들면 HELD이고 판정의 정책 코드·해제 방식·시각을 그대로 담는다")
    void hold_copiesDecision() {
        TransferHold hold = hold(TransferHoldDecision.holdUntil("DELAYED", NOW.plusHours(3)));

        assertThat(hold.getStatus()).isEqualTo(TransferHoldStatus.HELD);
        assertThat(hold.getPolicyCode()).isEqualTo("DELAYED");
        assertThat(hold.getReleaseMode()).isEqualTo(TransferHoldReleaseMode.AT_TIME);
        assertThat(hold.getReleaseAt()).isEqualTo(NOW.plusHours(3));
        assertThat(hold.getTransferHoldId()).isNull();
        assertThat(hold.getTransferId()).isNull();
    }

    @Test
    @DisplayName("바로 실행 판정으로는 보류를 만들 수 없다")
    void hold_rejectsProceedDecision() {
        assertThatThrownBy(() -> hold(TransferHoldDecision.proceed())).isInstanceOf(BusinessException.class);
    }

    private static TransferHold hold(TransferHoldDecision decision) {
        return TransferHold.hold(
                1L, 101L, "110222222222", 1_000_000L, "생활비", "홍길동", TransferChannel.WB, null, decision, NOW, NOW);
    }
}
