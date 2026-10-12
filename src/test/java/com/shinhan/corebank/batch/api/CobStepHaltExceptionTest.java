package com.shinhan.corebank.batch.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("COB 마감 중단 신호 예외 단위 테스트")
class CobStepHaltExceptionTest {

    @Test
    @DisplayName("메시지만 받으면 cause 없이 메시지를 보관한다")
    void createsWithMessageOnly() {
        CobStepHaltException exception = new CobStepHaltException("불일치 임계 초과");

        assertThat(exception.getMessage()).isEqualTo("불일치 임계 초과");
        assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("메시지와 원인 예외를 함께 받으면 둘 다 보관한다")
    void createsWithMessageAndCause() {
        IllegalStateException cause = new IllegalStateException("boom");

        CobStepHaltException exception = new CobStepHaltException("불일치 임계 초과", cause);

        assertThat(exception.getMessage()).isEqualTo("불일치 임계 초과");
        assertThat(exception.getCause()).isSameAs(cause);
    }
}
