package com.shinhan.corebank.common.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import java.time.LocalDateTime;
import lombok.Builder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * BEFORE_COMMIT 리스너의 계약을 DB 로 확인한다 — 이벤트로 남긴 행이 업무 행과 함께 커밋되고, 함께 롤백되고,
 * 트랜잭션이 없으면 아예 남지 않는다. 마지막 항목이 「발행은 반드시 업무 트랜잭션 안에서」(#436 결정 1·2)의 근거다.
 *
 * <p>outbox 테이블은 #437 몫이라, sink 가 테스트 전용 probe 테이블에 INSERT 하게 바꿔 끼운다.
 */
class DomainEventBeforeCommitListenerTest extends IntegrationTestSupport {

    private static final long REF_ID = 4360L;

    @Autowired
    ApplicationEventPublisher publisher;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    ProbeSink sink;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS domain_event_probe ("
                + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, kind VARCHAR(16) NOT NULL, ref_id BIGINT NOT NULL)");
        jdbcTemplate.update("DELETE FROM domain_event_probe");
        sink.failNext = false;
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM domain_event_probe");
    }

    @Test
    @DisplayName("트랜잭션 안에서 발행하면 이벤트 행이 업무 행과 함께 커밋된다")
    void publishInsideTransaction_recordsEventWithBusinessRow() {
        transactionTemplate(TransactionDefinition.PROPAGATION_REQUIRED).executeWithoutResult(status -> {
            insertBusinessRow();
            publisher.publishEvent(probeEvent());
        });

        assertThat(count("BUSINESS")).isEqualTo(1);
        assertThat(count("EVENT")).isEqualTo(1);
    }

    @Test
    @DisplayName("sink 가 실패하면 업무 행까지 함께 롤백된다 — 이벤트를 못 남기면 업무도 커밋되지 않는다")
    void sinkFailure_rollsBackBusinessRow() {
        sink.failNext = true;

        assertThatThrownBy(() -> transactionTemplate(TransactionDefinition.PROPAGATION_REQUIRED)
                        .executeWithoutResult(status -> {
                            insertBusinessRow();
                            publisher.publishEvent(probeEvent());
                        }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(count("BUSINESS")).isZero();
        assertThat(count("EVENT")).isZero();
    }

    @Test
    @DisplayName("TransactionTemplate 으로 연 REQUIRES_NEW 트랜잭션에도 리스너가 붙는다 — 이체 엔진의 기표 트랜잭션 방식")
    void publishInsideRequiresNewTemplate_recordsEvent() {
        transactionTemplate(TransactionDefinition.PROPAGATION_REQUIRES_NEW).executeWithoutResult(status -> {
            insertBusinessRow();
            publisher.publishEvent(probeEvent());
        });

        assertThat(count("BUSINESS")).isEqualTo(1);
        assertThat(count("EVENT")).isEqualTo(1);
    }

    @Test
    @DisplayName("트랜잭션 밖에서 발행하면 아무것도 남지 않는다 — 컨트롤러에서 execute() 뒤에 던지면 안 되는 이유")
    void publishOutsideTransaction_recordsNothing() {
        publisher.publishEvent(probeEvent());

        assertThat(count("EVENT")).isZero();
    }

    private TransactionTemplate transactionTemplate(int propagation) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(propagation);
        return template;
    }

    private void insertBusinessRow() {
        jdbcTemplate.update("INSERT INTO domain_event_probe (kind, ref_id) VALUES ('BUSINESS', ?)", REF_ID);
    }

    private int count(String kind) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM domain_event_probe WHERE kind = ? AND ref_id = ?", Integer.class, kind, REF_ID);
        return n == null ? 0 : n;
    }

    private static ProbeSettled probeEvent() {
        return ProbeSettled.builder()
                .customerId(408L)
                .refId(REF_ID)
                .status(ProcessResultStatus.SUCCESS)
                .occurredAt(LocalDateTime.of(2026, 9, 27, 12, 0))
                .build();
    }

    @Builder
    record ProbeSettled(
            Long customerId, Long refId, ProcessResultStatus status, String errorCode, LocalDateTime occurredAt)
            implements DomainEvent {}

    /** 이벤트를 probe 테이블에 남긴다. failNext 가 켜져 있으면 한 번 실패해 롤백 경로를 재현한다. */
    static class ProbeSink implements DomainEventSink {

        private final JdbcTemplate jdbcTemplate;
        volatile boolean failNext;

        ProbeSink(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        @Override
        public void record(DomainEvent event) {
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("sink 실패 재현");
            }
            jdbcTemplate.update("INSERT INTO domain_event_probe (kind, ref_id) VALUES ('EVENT', ?)", event.refId());
        }
    }

    @TestConfiguration
    static class ProbeSinkConfig {

        @Bean
        @Primary
        ProbeSink probeSink(JdbcTemplate jdbcTemplate) {
            return new ProbeSink(jdbcTemplate);
        }
    }
}
