package com.shinhan.corebank.limit.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.limit.api.TransferLimitRegistration;
import com.shinhan.corebank.limit.application.port.out.TransferLimitCommandPort;
import com.shinhan.corebank.limit.domain.TransferLimit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * limit/api 의 registerDefault 가 약속한 것은 "부여"지 "초기화"가 아니다. 그 약속을 고정한다 -
 * 공개 계약이라 signup 말고 다른 모듈이 불러도 되기 때문이다.
 * 커밋 여부 자체가 검증 대상이라 클래스에 @Transactional 을 걸지 않는다.
 */
@DisplayName("TransferLimitRegistration.registerDefault 계약 테스트")
class TransferLimitRegisterDefaultTest extends IntegrationTestSupport {

    private static final long CUSTOMER_ID = 9301L;

    @Autowired
    private TransferLimitRegistration transferLimitRegistration;

    @Autowired
    private TransferLimitCommandPort commandPort;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUpCommittedData() {
        jdbcTemplate.update("DELETE FROM transfer_limit WHERE customer_id = ?", CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM customer WHERE customer_id = ?", CUSTOMER_ID);
    }

    @Test
    @DisplayName("고객이 한도를 올린 뒤 기본값 부여가 다시 불려도 올린 값이 유지된다")
    void registerDefault_afterCustomerRaisedLimit_keepsRaisedValue() {
        // given - 고객이 1회 300만 / 1일 1000만으로 올려 둔 상태.
        // update 는 잠근 행에만 쓰는 계약이라 행을 먼저 만든 뒤 올린다
        seedCustomerWithoutLimit();
        transactionTemplate.executeWithoutResult(status -> {
            commandPort.saveIfAbsent(TransferLimit.create(CUSTOMER_ID));
            commandPort.update(TransferLimit.restore(CUSTOMER_ID, 3_000_000L, 10_000_000L));
        });

        // when
        transactionTemplate.executeWithoutResult(status -> transferLimitRegistration.registerDefault(CUSTOMER_ID));

        // then - 덮어쓰는 연산을 쓰면 여기서 100만 / 500만으로 되돌아간다
        assertThat(savedLimits()).containsExactly(3_000_000L, 10_000_000L);
    }

    private void seedCustomerWithoutLimit() {
        jdbcTemplate.update(
                """
            INSERT INTO customer (customer_id, user_id, password_hash, user_name, birth_date, email, phone_number, joined_at, created_at, updated_at)
            VALUES (?, 'limit9301', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', '한도부여테스터', '1990-01-01', 'limit9301@test.com', '01099999301', NOW(6), NOW(6), NOW(6))
            ON DUPLICATE KEY UPDATE customer_id = customer_id
            """,
                CUSTOMER_ID);
    }

    private List<Long> savedLimits() {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT one_time_limit, daily_limit FROM transfer_limit WHERE customer_id = ?", CUSTOMER_ID);
        return List.of(((Number) row.get("one_time_limit")).longValue(), ((Number) row.get("daily_limit")).longValue());
    }
}
