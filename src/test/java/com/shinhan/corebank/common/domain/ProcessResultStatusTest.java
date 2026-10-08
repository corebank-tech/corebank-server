package com.shinhan.corebank.common.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProcessResultStatusTest {

    @Test
    @DisplayName("SUCCESS·ERROR만 확정이다")
    void successAndErrorAreConfirmed() {
        assertThat(ProcessResultStatus.SUCCESS.isConfirmed()).isTrue();
        assertThat(ProcessResultStatus.ERROR.isConfirmed()).isTrue();
    }

    @Test
    @DisplayName("PROCESSING은 확정이 아니다")
    void processingIsNotConfirmed() {
        assertThat(ProcessResultStatus.PROCESSING.isConfirmed()).isFalse();
    }

    @Test
    @DisplayName("TIMEOUT(처리 불명)은 확정이 아니다 — 배치가 결과를 모르는 거래를 ERROR로 굳히지 않게 한다")
    void timeoutIsNotConfirmed() {
        assertThat(ProcessResultStatus.TIMEOUT.isConfirmed()).isFalse();
    }
}
