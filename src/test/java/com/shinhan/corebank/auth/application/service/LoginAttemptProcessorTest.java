package com.shinhan.corebank.auth.application.service;

import com.shinhan.corebank.auth.domain.model.LoginAttemptResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("로그인 시도 결과 계산기 단위 테스트")
class LoginAttemptProcessorTest {

    private final LoginAttemptProcessor processor =
            new LoginAttemptProcessor();

    // 첫 번째 실패는 남은 시도 횟수 4회를 반환
    @Test
    @DisplayName("실패 1회는 errorCount 1과 remainingAttempts 4를 반환한다")
    void processesFirstFailure() {
        LoginAttemptResult result = processor.process(1);

        assertThat(result).isEqualTo(new LoginAttemptResult(1, 4));
    }

    // 잠금 직전 실패는 남은 시도 횟수 1회를 반환
    @Test
    @DisplayName("실패 4회는 errorCount 4와 remainingAttempts 1을 반환한다")
    void processesFourthFailure() {
        LoginAttemptResult result = processor.process(4);

        assertThat(result).isEqualTo(new LoginAttemptResult(4, 1));
    }

    // 횟수 데이터가 노출되지 않는 범위는 계산을 거부
    @ParameterizedTest
    @ValueSource(ints = {0, 5, 6})
    @DisplayName("0회와 잠금 횟수 이상은 로그인 시도 결과로 변환하지 않는다")
    void rejectsNonExposableFailureCount(int errorCount) {
        assertThatThrownBy(() -> processor.process(errorCount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("로그인 실패 횟수는 1회 이상 4회 이하여야 합니다.");
    }
}
