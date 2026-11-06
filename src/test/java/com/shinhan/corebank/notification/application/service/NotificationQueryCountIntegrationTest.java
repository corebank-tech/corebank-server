package com.shinhan.corebank.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.support.CustomerTestFixture;
import com.shinhan.corebank.notification.application.port.in.NotificationQueryUseCase;
import com.shinhan.corebank.notification.application.port.in.dto.NotificationListResult;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

// 스케줄러 문장이 섞이지 않게 호출 스레드의 문장만 센다. 설정이 달라 컨텍스트를 따로 띄운다.
@TestPropertySource(
        properties =
                "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.shinhan.corebank.notification.application.service.NotificationQueryCountIntegrationTest$CallerThreadStatementCounter")
class NotificationQueryCountIntegrationTest extends IntegrationTestSupport {

    private static final int PAGE_SIZE = 20;
    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 11, 7, 10, 0);

    @Autowired
    NotificationQueryUseCase notificationQueryUseCase;

    @Autowired
    CustomerTestFixture customerFixture;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Long customerId;

    @BeforeEach
    void setUp() {
        customerId = customerFixture.createCustomer();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM notification WHERE customer_id = ?", customerId);
        customerFixture.deleteCustomer(customerId);
    }

    @Test
    @DisplayName("다음 페이지가 있으면 20건을 목록·전체 건수·미읽음 건수 3문장으로 조회한다")
    void getNotifications_fullFirstPage_usesThreeStatements() {
        insertUnread(PAGE_SIZE + 1);

        final NotificationListResult result = getFirstPageCountingStatements();

        assertThat(result.notifications().getContent()).hasSize(PAGE_SIZE);
        assertThat(result.notifications().getTotalElements()).isEqualTo(PAGE_SIZE + 1L);
        assertThat(CallerThreadStatementCounter.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("첫 페이지가 덜 차면 전체 건수 쿼리를 건너뛰어 2문장으로 조회한다")
    void getNotifications_partialFirstPage_skipsTotalCountStatement() {
        insertUnread(PAGE_SIZE - 1);

        final NotificationListResult result = getFirstPageCountingStatements();

        assertThat(result.notifications().getTotalElements()).isEqualTo(PAGE_SIZE - 1L);
        assertThat(CallerThreadStatementCounter.count()).isEqualTo(2);
    }

    private NotificationListResult getFirstPageCountingStatements() {
        CallerThreadStatementCounter.reset();
        return notificationQueryUseCase.getNotifications(customerId, 0, PAGE_SIZE, false);
    }

    private void insertUnread(int count) {
        for (int minute = 0; minute < count; minute++) {
            jdbcTemplate.update(
                    """
                    INSERT INTO notification (customer_id, type, title, content, is_read, occurred_at)
                    VALUES (?, 'TRANSFER', '알림 제목', '알림 본문', FALSE, ?)
                    """,
                    customerId,
                    Timestamp.valueOf(BASE_TIME.plusMinutes(minute)));
        }
    }

    public static class CallerThreadStatementCounter implements StatementInspector {

        private static final ThreadLocal<Integer> STATEMENT_COUNT = ThreadLocal.withInitial(() -> 0);

        static int count() {
            return STATEMENT_COUNT.get();
        }

        static void reset() {
            STATEMENT_COUNT.set(0);
        }

        @Override
        public String inspect(String sql) {
            STATEMENT_COUNT.set(STATEMENT_COUNT.get() + 1);
            return sql;
        }
    }
}
