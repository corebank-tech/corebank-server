package com.shinhan.corebank.account.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.domain.AccountPasswordAuthTokenPayload;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

// 계좌비밀번호 인증 토큰의 고객·계좌 일치 소비와 고객 기준 소비를 MySQL에서 검증한다.
class AccountPasswordAuthTokenPersistenceAdapterTest extends IntegrationTestSupport {

    private static final Long CUSTOMER_ID = 958_001L;
    private static final Long ACCOUNT_ID = 958_101L;
    private static final Duration TTL = Duration.ofMinutes(5);

    @Autowired
    AccountPasswordAuthTokenPersistenceAdapter adapter;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private final String token = "APW_TEST_" + UUID.randomUUID();

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update(
                "DELETE FROM auth_token WHERE purpose = 'ACCOUNT_PASSWORD_AUTH' AND customer_id IN (?, ?)",
                CUSTOMER_ID,
                Long.MAX_VALUE);
    }

    @Test
    @DisplayName("고객 또는 계좌가 다르면 소비하지 않고, 일치하면 한 번만 소비한다")
    void consumeIfMatches_onlyOnceWhenCustomerAndAccountMatch() {
        adapter.save(token, new AccountPasswordAuthTokenPayload(CUSTOMER_ID, ACCOUNT_ID), TTL);

        assertThat(adapter.consumeIfMatches(token, new AccountPasswordAuthTokenPayload(CUSTOMER_ID + 1, ACCOUNT_ID)))
                .isFalse();
        assertThat(adapter.consumeIfMatches(token, new AccountPasswordAuthTokenPayload(CUSTOMER_ID, ACCOUNT_ID + 1)))
                .isFalse();
        assertThat(adapter.consumeIfMatches(token, new AccountPasswordAuthTokenPayload(CUSTOMER_ID, ACCOUNT_ID)))
                .isTrue();
        assertThat(adapter.consumeIfMatches(token, new AccountPasswordAuthTokenPayload(CUSTOMER_ID, ACCOUNT_ID)))
                .isFalse();
    }

    @Test
    @DisplayName("고객 기준 소비는 계좌와 무관하게 같은 고객일 때 한 번만 성공한다")
    void consumeIfCustomerMatches_onlyOnceForSameCustomer() {
        adapter.save(token, new AccountPasswordAuthTokenPayload(CUSTOMER_ID, ACCOUNT_ID), TTL);

        assertThat(adapter.consumeIfCustomerMatches(token, CUSTOMER_ID + 1)).isFalse();
        assertThat(adapter.consumeIfCustomerMatches(token, CUSTOMER_ID)).isTrue();
        assertThat(adapter.consumeIfCustomerMatches(token, CUSTOMER_ID)).isFalse();
    }

    @Test
    @DisplayName("Long 범위의 고객 ID도 손실 없이 비교한다")
    void consumeIfCustomerMatches_withMaxLongCustomerId() {
        adapter.save(token, new AccountPasswordAuthTokenPayload(Long.MAX_VALUE, ACCOUNT_ID), TTL);

        assertThat(adapter.consumeIfCustomerMatches(token, Long.MAX_VALUE - 1)).isFalse();
        assertThat(adapter.consumeIfCustomerMatches(token, Long.MAX_VALUE)).isTrue();
    }
}
