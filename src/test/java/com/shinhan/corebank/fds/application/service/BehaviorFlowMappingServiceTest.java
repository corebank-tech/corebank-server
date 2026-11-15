package com.shinhan.corebank.fds.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.fds.application.port.out.BehaviorFlowMappingPort;
import com.shinhan.corebank.fds.application.port.out.BehaviorFlowPort;
import com.shinhan.corebank.fds.application.port.out.BehaviorFlowPort.Flow;
import com.shinhan.corebank.fds.domain.exception.FdsErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BehaviorFlowMappingServiceTest {

    private final String flowId = UUID.randomUUID().toString();
    private final MemoryFlowPort flows = new MemoryFlowPort(flowId, 975001L);
    private final MemoryMappingPort mappings = new MemoryMappingPort();
    private final BehaviorFlowMappingService service = new BehaviorFlowMappingService(
            flows, mappings, Clock.fixed(Instant.parse("2026-11-11T00:00:00Z"), ZoneId.of("Asia/Seoul")));

    @Test
    void savesAndFindsSameMappingIdempotentlyInBothDirections() {
        assertThat(service.save(975000001L, flowId, 975001L).flowId()).isEqualTo(flowId);
        assertThat(service.save(975000001L, flowId, 975001L).refId()).isEqualTo(975000001L);

        assertThat(service.findByRefId(975000001L).flowId()).isEqualTo(flowId);
        assertThat(service.findByFlowId(flowId).refId()).isEqualTo(975000001L);
        assertThat(mappings.byRef).hasSize(1);
    }

    @Test
    void rejectsMissingOrOtherCustomersFlowWithoutRevealingWhich() {
        flows.flow = null;
        assertThatThrownBy(() -> service.save(975000001L, flowId, 975001L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(FdsErrorCode.NOT_FOUND);

        flows.flow = new Flow(flowId, 975002L, LocalDateTime.now(), null, null);
        assertThatThrownBy(() -> service.save(975000001L, flowId, 975001L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(FdsErrorCode.NOT_FOUND);
    }

    @Test
    void rejectsDifferentFlowForExistingRefWithoutOverwrite() {
        service.save(975000001L, flowId, 975001L);
        String otherFlow = UUID.randomUUID().toString();
        flows.flow = new Flow(otherFlow, 975001L, LocalDateTime.now(), null, null);

        assertThatThrownBy(() -> service.save(975000001L, otherFlow, 975001L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(FdsErrorCode.FLOW_CONFLICT);
        assertThat(service.findByRefId(975000001L).flowId()).isEqualTo(flowId);
    }

    @Test
    void validatesPublicContractInputsAndReturnsNullWhenAbsent() {
        assertThatThrownBy(() -> service.save(0, flowId, 975001L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.save(1, "not-uuid", 975001L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.findByRefId(0)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.findByFlowId("not-uuid")).isInstanceOf(BusinessException.class);
        assertThat(service.findByRefId(975000099L)).isNull();
    }

    private static final class MemoryFlowPort implements BehaviorFlowPort {
        private Flow flow;

        private MemoryFlowPort(String flowId, long customerId) {
            flow = new Flow(flowId, customerId, LocalDateTime.now(), null, null);
        }

        @Override
        public Flow find(String id) {
            return flow != null && flow.flowId().equals(id) ? flow : null;
        }

        @Override
        public void create(String flowId, long customerId, LocalDateTime receivedAt) {}

        @Override
        public void lock(String flowId) {}

        @Override
        public void append(String flowId, Event event, LocalDateTime receivedAt) {}

        @Override
        public void finalizeFlow(String flowId, Integer confirmActiveSeconds, LocalDateTime receivedAt) {}

        @Override
        public List<Event> events(String flowId) {
            return List.of();
        }
    }

    private static final class MemoryMappingPort implements BehaviorFlowMappingPort {
        private final Map<Long, Mapping> byRef = new HashMap<>();
        private final Map<String, Mapping> byFlow = new HashMap<>();

        @Override
        public void insertOrKeepExisting(long refId, String flowId, long customerId, LocalDateTime createdAt) {
            if (byRef.containsKey(refId) || byFlow.containsKey(flowId)) return;
            Mapping mapping = new Mapping(refId, flowId, customerId);
            byRef.put(refId, mapping);
            byFlow.put(flowId, mapping);
        }

        @Override
        public Mapping findByRefId(long refId) {
            return byRef.get(refId);
        }

        @Override
        public Mapping findByRefIdForUpdate(long refId) {
            return byRef.get(refId);
        }

        @Override
        public Mapping findByFlowId(String flowId) {
            return byFlow.get(flowId);
        }
    }
}
