package com.shinhan.corebank.paymentstop.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.paymentstop.application.port.out.AgentRunPort;
import com.shinhan.corebank.paymentstop.application.port.out.DocumentStoragePort;
import com.shinhan.corebank.paymentstop.application.port.out.LedgerFactPort;
import com.shinhan.corebank.paymentstop.application.port.out.ObjectionDocumentPort;
import com.shinhan.corebank.paymentstop.application.port.out.ObjectionReviewPort;
import com.shinhan.corebank.paymentstop.application.port.out.PaymentStopRecordPort;
import com.shinhan.corebank.paymentstop.config.AgentProperties;
import com.shinhan.corebank.paymentstop.domain.exception.PaymentStopErrorCode;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AgentJobServiceKillSwitchTest {

    private static final String KEY = "unit-test-key";

    private final AgentRunPort agentRunPort = mock(AgentRunPort.class);

    @Test
    @DisplayName("킬스위치가 꺼져 있으면 워커 키가 맞아도 대기열을 보지 않고 빈 응답이다")
    void disabledReturnsEmptyWithoutTouchingQueue() {
        AgentJobService service = service(false);

        assertThat(service.claim(KEY)).isEmpty();
        verifyNoInteractions(agentRunPort);
    }

    @Test
    @DisplayName("킬스위치가 꺼져 있어도 워커 키가 틀리면 먼저 PST0101로 거부한다")
    void disabledStillChecksWorkerKey() {
        assertThatThrownBy(() -> service(false).claim("wrong"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentStopErrorCode.WORKER_AUTH_FAILED);
    }

    private AgentJobService service(boolean enabled) {
        AgentProperties properties =
                new AgentProperties(enabled, KEY, Duration.ofMinutes(10), Duration.ofMinutes(5), 3);
        return new AgentJobService(
                agentRunPort,
                mock(PaymentStopRecordPort.class),
                mock(LedgerFactPort.class),
                mock(ObjectionReviewPort.class),
                mock(ObjectionDocumentPort.class),
                mock(DocumentStoragePort.class),
                mock(GateEvaluator.class),
                new JobTokenService(properties),
                properties,
                Clock.systemDefaultZone());
    }
}
