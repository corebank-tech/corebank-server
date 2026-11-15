package com.shinhan.corebank.paymentstop.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.paymentstop.config.AgentProperties;
import com.shinhan.corebank.paymentstop.domain.exception.PaymentStopErrorCode;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JobTokenServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 15, 0);

    private final JobTokenService service = new JobTokenService(properties("unit-test-key"));

    @Test
    @DisplayName("발급한 토큰은 실행, 이의제기, 시도 번호를 그대로 돌려준다")
    void roundTrip() {
        String token = service.issue(11L, 22L, 2, NOW);

        assertThat(service.verify(token, NOW.plusMinutes(9))).isEqualTo(new JobTokenService.JobClaims(11L, 22L, 2));
    }

    @Test
    @DisplayName("만료 시각이 지났거나, 서명이 바뀌었거나, 다른 키로 서명했으면 PST0102다")
    void rejectsExpiredTamperedOrForeignTokens() {
        String token = service.issue(11L, 22L, 1, NOW);
        JobTokenService other = new JobTokenService(properties("another-key"));

        assertInvalid(() -> service.verify(token, NOW.plusMinutes(10)));
        assertInvalid(() -> service.verify(token.substring(0, token.length() - 2) + "AA", NOW));
        assertInvalid(() -> other.verify(token, NOW));
        assertInvalid(() -> service.verify("not-a-token", NOW));
        assertInvalid(() -> service.verify(null, NOW));
    }

    @Test
    @DisplayName("워커 키가 비어 있으면 어떤 토큰도 받지 않고 워커 인증도 거부한다")
    void blankWorkerKeyRejectsEverything() {
        JobTokenService blank = new JobTokenService(properties(""));

        assertInvalid(() -> blank.verify("x.y", NOW));
        assertThatThrownBy(() -> blank.verifyWorkerKey(""))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentStopErrorCode.WORKER_AUTH_FAILED);
        assertThatThrownBy(() -> service.verifyWorkerKey("wrong"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentStopErrorCode.WORKER_AUTH_FAILED);
    }

    @Test
    @DisplayName("상대 가명 키는 같은 이의제기 안에서만 같고 다른 이의제기에서는 달라진다")
    void counterpartyKeyIsScopedToObjection() {
        assertThat(service.counterpartyKey(1L, 500L))
                .isEqualTo(service.counterpartyKey(1L, 500L))
                .isNotEqualTo(service.counterpartyKey(2L, 500L))
                .matches("CP-[0-9A-F]{8}");
    }

    private static void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentStopErrorCode.JOB_TOKEN_INVALID);
    }

    private static AgentProperties properties(String key) {
        return new AgentProperties(true, key, Duration.ofMinutes(10), Duration.ofMinutes(5), 3);
    }
}
