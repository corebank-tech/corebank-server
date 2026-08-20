package com.shinhan.corebank.otp.adapter.out.redis;

import com.shinhan.corebank.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// otpAuthToken의 300초 TTL과 요청 ID 일치 조건부 일회성 소비를 검증한다.
class OtpAuthTokenRedisAdapterTest extends IntegrationTestSupport {

    private static final String KEY_PREFIX = "otp:auth:";

    @Autowired OtpAuthTokenRedisAdapter adapter;
    @Autowired StringRedisTemplate redisTemplate;

    private String token;

    @AfterEach
    void cleanUp() {
        if (token != null) {
            redisTemplate.delete(KEY_PREFIX + token);
        }
    }

    @Test
    @DisplayName("otpAuthToken을 요청 ID와 함께 300초 동안 저장한다")
    void savesWithFiveMinuteTtl() {
        token = "OTP_AUTH_" + UUID.randomUUID();

        adapter.save(token, "OTP_REQ_test", Duration.ofMinutes(5));

        assertThat(adapter.findRequestId(token)).contains("OTP_REQ_test");
        assertThat(redisTemplate.getExpire(KEY_PREFIX + token, TimeUnit.SECONDS))
                .isBetween(295L, 300L);
    }

    @Test
    @DisplayName("예상 요청 ID가 다르면 토큰을 소진하지 않는다")
    void mismatchDoesNotConsumeToken() {
        token = "OTP_AUTH_" + UUID.randomUUID();
        adapter.save(token, "OTP_REQ_original", Duration.ofMinutes(5));

        assertThat(adapter.consumeIfMatches(token, "OTP_REQ_other")).isFalse();
        assertThat(adapter.findRequestId(token)).contains("OTP_REQ_original");
    }

    @Test
    @DisplayName("일치하는 토큰은 한 번만 소비할 수 있다")
    void consumesMatchingTokenOnce() {
        token = "OTP_AUTH_" + UUID.randomUUID();
        adapter.save(token, "OTP_REQ_test", Duration.ofMinutes(5));

        assertThat(adapter.consumeIfMatches(token, "OTP_REQ_test")).isTrue();
        assertThat(adapter.consumeIfMatches(token, "OTP_REQ_test")).isFalse();
    }
}
