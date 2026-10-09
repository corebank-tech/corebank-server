package com.shinhan.corebank.otp.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.otp.domain.model.OtpAuthTokenPayload;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

// otpAuthToken의 조회와 payload 일치 소비를 MySQL에서 검증한다.
class OtpAuthTokenPersistenceAdapterTest extends IntegrationTestSupport {

    private static final Long CUSTOMER_ID = 958_002L;
    private static final Duration TTL = Duration.ofMinutes(5);

    @Autowired
    OtpAuthTokenPersistenceAdapter adapter;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private final String token = "OTP_TEST_" + UUID.randomUUID();

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM auth_token WHERE purpose = 'OTP_AUTH' AND customer_id = ?", CUSTOMER_ID);
    }

    @Test
    @DisplayName("저장한 otpAuthToken을 요청 ID·고객 ID payload로 조회하고 고객 ID를 컬럼에 남긴다")
    void save_thenFind() {
        OtpAuthTokenPayload payload = new OtpAuthTokenPayload("OTP_REQ_1", CUSTOMER_ID);

        adapter.save(token, payload, TTL);

        assertThat(adapter.find(token)).contains(payload);
        Long ttlSeconds = jdbcTemplate.queryForObject(
                """
                SELECT TIMESTAMPDIFF(SECOND, created_at, expires_at) FROM auth_token
                 WHERE purpose = 'OTP_AUTH' AND customer_id = ?
                """,
                Long.class,
                CUSTOMER_ID);
        assertThat(ttlSeconds).isEqualTo(TTL.toSeconds());
    }

    @Test
    @DisplayName("요청 ID 또는 고객이 다르면 소비하지 않고, 같을 때만 한 번 소비한다")
    void consumeIfMatches_onlyOnceWhenPayloadEquals() {
        adapter.save(token, new OtpAuthTokenPayload("OTP_REQ_1", CUSTOMER_ID), TTL);

        assertThat(adapter.consumeIfMatches(token, new OtpAuthTokenPayload("OTP_REQ_2", CUSTOMER_ID)))
                .isFalse();
        assertThat(adapter.consumeIfMatches(token, new OtpAuthTokenPayload("OTP_REQ_1", CUSTOMER_ID + 1)))
                .isFalse();
        assertThat(adapter.consumeIfMatches(token, new OtpAuthTokenPayload("OTP_REQ_1", CUSTOMER_ID)))
                .isTrue();
        assertThat(adapter.consumeIfMatches(token, new OtpAuthTokenPayload("OTP_REQ_1", CUSTOMER_ID)))
                .isFalse();
        assertThat(adapter.find(token)).isEmpty();
    }
}
