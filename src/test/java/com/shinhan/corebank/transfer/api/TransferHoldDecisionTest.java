package com.shinhan.corebank.transfer.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TransferHoldDecisionTest {

    private static final LocalDateTime RELEASE_AT = LocalDateTime.of(2026, 10, 29, 15, 0);

    @Test
    @DisplayName("바로 실행은 보류 값이 모두 비어 있다")
    void proceed_hasNoHoldValues() {
        TransferHoldDecision decision = TransferHoldDecision.proceed();

        assertThat(decision.hold()).isFalse();
        assertThat(decision.policyCode()).isNull();
        assertThat(decision.releaseMode()).isNull();
        assertThat(decision.releaseAt()).isNull();
    }

    @Test
    @DisplayName("시각 해제 보류는 실행 시각을 갖는다")
    void holdUntil_carriesReleaseAt() {
        TransferHoldDecision decision = TransferHoldDecision.holdUntil("DELAYED", RELEASE_AT);

        assertThat(decision.hold()).isTrue();
        assertThat(decision.releaseMode()).isEqualTo(TransferHoldReleaseMode.AT_TIME);
        assertThat(decision.releaseAt()).isEqualTo(RELEASE_AT);
    }

    @Test
    @DisplayName("승인 해제 보류는 실행 시각이 없다")
    void holdForApproval_hasNoReleaseAt() {
        TransferHoldDecision decision = TransferHoldDecision.holdForApproval("FDS_A");

        assertThat(decision.hold()).isTrue();
        assertThat(decision.releaseMode()).isEqualTo(TransferHoldReleaseMode.ON_APPROVAL);
        assertThat(decision.releaseAt()).isNull();
    }

    @Test
    @DisplayName("시각 해제인데 실행 시각이 없으면 내부 오류로 막는다")
    void holdUntil_withoutReleaseAt_throwsInternalError() {
        assertInternalError(() -> TransferHoldDecision.holdUntil("DELAYED", null));
    }

    @Test
    @DisplayName("보류인데 정책 코드가 비어 있으면 내부 오류로 막는다")
    void hold_withBlankPolicyCode_throwsInternalError() {
        assertInternalError(() -> TransferHoldDecision.holdForApproval(" "));
    }

    @Test
    @DisplayName("승인 해제에 실행 시각을 넣으면 내부 오류로 막는다")
    void onApproval_withReleaseAt_throwsInternalError() {
        assertInternalError(
                () -> new TransferHoldDecision(true, "FDS_A", TransferHoldReleaseMode.ON_APPROVAL, RELEASE_AT));
    }

    @Test
    @DisplayName("바로 실행에 보류 값을 섞으면 내부 오류로 막는다")
    void proceed_withHoldValues_throwsInternalError() {
        assertInternalError(() -> new TransferHoldDecision(false, "FDS_A", null, null));
    }

    private void assertInternalError(Runnable creation) {
        assertThatThrownBy(creation::run).isInstanceOf(BusinessException.class).satisfies(e -> assertThat(
                        ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INTERNAL_ERROR));
    }
}
