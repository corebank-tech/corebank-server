package com.shinhan.corebank.common.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Builder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 커밋 직후 AFTER_COMMIT 리스너가 예외로 끝나도 EVENT_PUBLICATION 기록이 남아 재제출로 전달되는지 확인한다(#437).
 * 리스너 예외로 커밋 직후 장애를 재현하며, 도입 전 · 후 결과는 ADR-0010 「검증」에 있다.
 */
class DomainEventPublicationRegistryTest extends IntegrationTestSupport {

    private static final long REF_ID = 4370L;

    @Autowired
    ApplicationEventPublisher publisher;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    IncompleteEventPublications incompletePublications;

    @Autowired
    ProbeListener listener;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS domain_event_probe ("
                + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, kind VARCHAR(16) NOT NULL, ref_id BIGINT NOT NULL)");
        jdbcTemplate.update("DELETE FROM domain_event_probe");
        deleteProbePublications();
        listener.reset();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM domain_event_probe");
        deleteProbePublications();
    }

    @Test
    @DisplayName("커밋 직후 리스너가 예외로 끝나도 발행 기록이 남는다 — 업무는 커밋되고 이벤트는 아직 전달 전이다")
    void listenerFailsAfterCommit_publicationRemains() {
        listener.failNextDelivery();

        publishInsideBusinessTransaction();

        assertThat(probeRowCount("BUSINESS")).isEqualTo(1);
        assertThat(listener.attempts()).isEqualTo(1);
        assertThat(probeRowCount("DELIVERED")).isZero();
        assertThat(probePublicationCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("남은 발행 기록을 재제출하면 이벤트가 전달되고 기록이 지워진다")
    void resubmitRemainingPublication_deliversEventAndDeletesRecord() {
        listener.failNextDelivery();
        publishInsideBusinessTransaction();

        incompletePublications.resubmitIncompletePublications(
                publication -> publication.getEvent() instanceof ProbeSettled);

        assertThat(listener.attempts()).isEqualTo(2);
        assertThat(probeRowCount("DELIVERED")).isEqualTo(1);
        assertThat(probePublicationCount()).isZero();
    }

    @Test
    @DisplayName("리스너가 성공하면 발행 기록이 바로 지워진다 — completion-mode=delete")
    void listenerSucceeds_deletesRecord() {
        publishInsideBusinessTransaction();

        assertThat(probeRowCount("DELIVERED")).isEqualTo(1);
        assertThat(probePublicationCount()).isZero();
    }

    @Test
    @DisplayName("업무 트랜잭션이 롤백되면 발행 기록도 남지 않는다 — 업무와 기록이 같은 커밋에 실린다")
    void businessRollsBack_leavesNoRecord() {
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    insertBusinessRow();
                    publisher.publishEvent(probeEvent());
                    throw new IllegalStateException("업무 실패 재현");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(probeRowCount("BUSINESS")).isZero();
        assertThat(listener.attempts()).isZero();
        assertThat(probePublicationCount()).isZero();
    }

    private void publishInsideBusinessTransaction() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            insertBusinessRow();
            publisher.publishEvent(probeEvent());
        });
    }

    private void insertBusinessRow() {
        jdbcTemplate.update("INSERT INTO domain_event_probe (kind, ref_id) VALUES ('BUSINESS', ?)", REF_ID);
    }

    private int probePublicationCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM EVENT_PUBLICATION WHERE EVENT_TYPE = ?",
                Integer.class,
                ProbeSettled.class.getName());
    }

    private void deleteProbePublications() {
        jdbcTemplate.update("DELETE FROM EVENT_PUBLICATION WHERE EVENT_TYPE = ?", ProbeSettled.class.getName());
    }

    private int probeRowCount(String kind) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM domain_event_probe WHERE kind = ? AND ref_id = ?", Integer.class, kind, REF_ID);
    }

    private static ProbeSettled probeEvent() {
        return ProbeSettled.builder()
                .customerId(408L)
                .refId(REF_ID)
                .status(ProcessResultStatus.SUCCESS)
                .occurredAt(LocalDateTime.of(2026, 10, 19, 12, 0))
                .build();
    }

    @Builder
    record ProbeSettled(
            Long customerId, Long refId, ProcessResultStatus status, String errorCode, LocalDateTime occurredAt)
            implements DomainEvent {}

    /**
     * 받은 이벤트를 DELIVERED 행으로 남기고, failNext 가 켜져 있으면 한 번 예외로 끝나 커밋 직후 장애를 재현한다.
     * Spring Modulith 가 이 빈을 프록시로 감싸므로 테스트는 필드가 아니라 public 메서드로 상태를 다룬다.
     */
    static class ProbeListener {

        private final JdbcTemplate jdbcTemplate;
        private final AtomicInteger attempts = new AtomicInteger();
        private volatile boolean failNext;

        ProbeListener(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        // AFTER_COMMIT 시점에는 원래 트랜잭션이 이미 커밋돼 있어, 쓰기는 새 트랜잭션에서 한다(PH-96-② 구독자와 같은 구성).
        @Transactional(propagation = Propagation.REQUIRES_NEW)
        @TransactionalEventListener
        public void on(ProbeSettled event) {
            attempts.incrementAndGet();
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("커밋 직후 리스너 장애 재현");
            }
            jdbcTemplate.update("INSERT INTO domain_event_probe (kind, ref_id) VALUES ('DELIVERED', ?)", event.refId());
        }

        public void failNextDelivery() {
            failNext = true;
        }

        public int attempts() {
            return attempts.get();
        }

        public void reset() {
            attempts.set(0);
            failNext = false;
        }
    }

    @TestConfiguration
    static class ProbeListenerConfig {

        @Bean
        ProbeListener probeListener(JdbcTemplate jdbcTemplate) {
            return new ProbeListener(jdbcTemplate);
        }
    }
}
