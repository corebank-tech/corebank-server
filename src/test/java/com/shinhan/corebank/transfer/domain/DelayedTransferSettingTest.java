package com.shinhan.corebank.transfer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("지연이체 설정 도메인")
class DelayedTransferSettingTest {

    private static final DelayedTransferSetting ON = DelayedTransferSetting.of(1L, true, 1_000_000L, 5);

    @ParameterizedTest(name = "기준금액 {0}원·지연 {1}시간은 거부한다")
    @CsvSource({"0, 3", "-1, 3", "1, 2", "1, 25"})
    void of_rejectsOutOfRange(long thresholdAmount, int delayHours) {
        assertThatThrownBy(() -> DelayedTransferSetting.of(1L, true, thresholdAmount, delayHours))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(TransferErrorCode.INVALID_DELAYED_TRANSFER_SETTING));
    }

    @Test
    @DisplayName("경계값 1원·3시간·24시간은 허용한다")
    void of_acceptsBoundaries() {
        DelayedTransferSetting.of(1L, true, 1L, 3);
        DelayedTransferSetting.of(1L, true, 1L, 24);
    }

    @Test
    @DisplayName("저장한 적 없는 고객은 꺼져 있고 기본값은 100만 원·3시간이다")
    void defaults() {
        DelayedTransferSetting defaults = DelayedTransferSetting.defaultsFor(1L);

        assertThat(defaults.isEnabled()).isFalse();
        assertThat(defaults.getThresholdAmount()).isEqualTo(1_000_000L);
        assertThat(defaults.getDelayHours()).isEqualTo(3);
        assertThat(defaults.appliesTo(Long.MAX_VALUE)).isFalse();
    }

    @Test
    @DisplayName("켜져 있으면 기준금액 이상(같으면 포함)만 지연하고, 실행 시각은 요청 시각 + 지연시간이다")
    void appliesTo_isInclusive() {
        assertThat(ON.appliesTo(1_000_000L)).isTrue();
        assertThat(ON.appliesTo(999_999L)).isFalse();
        assertThat(ON.releaseAt(LocalDateTime.of(2031, 7, 1, 10, 0))).isEqualTo(LocalDateTime.of(2031, 7, 1, 15, 0));
    }

    @Test
    @DisplayName("켠 상태에서 끄기·기준금액 올리기·지연시간 줄이기는 완화다")
    void loosens_whenWeakeningProtection() {
        assertThat(DelayedTransferSetting.of(1L, false, 1_000_000L, 5).loosens(ON))
                .isTrue();
        assertThat(DelayedTransferSetting.of(1L, true, 1_000_001L, 5).loosens(ON))
                .isTrue();
        assertThat(DelayedTransferSetting.of(1L, true, 1_000_000L, 4).loosens(ON))
                .isTrue();
    }

    @Test
    @DisplayName("켜기·기준금액 내리기·지연시간 늘리기와, 꺼진 상태에서 숫자만 바꾸는 건 완화가 아니다")
    void loosens_falseWhenStrengtheningOrStillOff() {
        assertThat(ON.loosens(DelayedTransferSetting.defaultsFor(1L))).isFalse();
        assertThat(DelayedTransferSetting.of(1L, true, 500_000L, 5).loosens(ON)).isFalse();
        assertThat(DelayedTransferSetting.of(1L, true, 1_000_000L, 24).loosens(ON))
                .isFalse();
        assertThat(DelayedTransferSetting.of(1L, false, 9_000_000L, 3).loosens(DelayedTransferSetting.defaultsFor(1L)))
                .isFalse();
    }
}
