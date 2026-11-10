package com.shinhan.corebank.fds.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.idempotency.IdempotentRequestExecutor;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.fds.application.port.out.BehaviorFlowPort;
import com.shinhan.corebank.fds.application.service.BehaviorFlowService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** 컨트롤러의 본문 크기·JSON 오류와 정상 제출을 HTTP 경계에서 확인한다. */
class BehaviorFlowControllerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-09T05:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final MemoryFlowPort port = new MemoryFlowPort();
    private final BehaviorFlowController controller = new BehaviorFlowController(
            new BehaviorFlowService(port, clock),
            new CurrentCustomerProvider() {
                @Override
                public AuthenticatedCustomer getCurrentCustomer() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public Long getCurrentCustomerId() {
                    return 951001L;
                }
            },
            new IdempotentRequestExecutor(null, mapper) {
                @Override
                public <T> ResponseEntity<ApiResponse<T>> execute(
                        String key,
                        Long customer,
                        String endpoint,
                        Object fingerprint,
                        TypeReference<ApiResponse<T>> responseType,
                        Supplier<ApiResponse<T>> action) {
                    return ResponseEntity.ok(action.get());
                }
            },
            mapper);

    @Test
    void acceptsValidJson() {
        UUID id = UUID.randomUUID();
        String body =
                """
                {"events":[{"flowId":"%s","seqNo":1,"eventType":"TRANSFER_ENTER",
                "occurredAt":"2026-11-09T13:59:59+09:00","step":"D-01"}]}
                """
                        .formatted(id);

        assertThat(controller.submit(id, "test-key", body).getBody().data().acceptedCount())
                .isEqualTo(1);
        assertThat(port.events).hasSize(1);
    }

    @Test
    void rejectsOversizedAndMalformedJsonBeforeSaving() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> controller.submit(id, "test-key", "x".repeat(32 * 1024 + 1)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> controller.submit(id, "test-key", "{")).isInstanceOf(BusinessException.class);
        assertThat(port.events).isEmpty();
    }

    private static class MemoryFlowPort implements BehaviorFlowPort {
        Flow flow;
        List<Event> events = new ArrayList<>();

        @Override
        public Flow find(String id) {
            return flow;
        }

        @Override
        public void create(String id, long customerId, LocalDateTime receivedAt) {
            if (flow == null) flow = new Flow(id, customerId, receivedAt, null, null);
        }

        @Override
        public void lock(String id) {}

        @Override
        public void append(String id, Event event, LocalDateTime receivedAt) {
            events.add(event);
        }

        @Override
        public void finalizeFlow(String id, Integer seconds, LocalDateTime receivedAt) {
            flow = new Flow(id, flow.customerId(), flow.firstReceivedAt(), receivedAt, seconds);
        }

        @Override
        public List<Event> events(String id) {
            return List.copyOf(events);
        }
    }
}
