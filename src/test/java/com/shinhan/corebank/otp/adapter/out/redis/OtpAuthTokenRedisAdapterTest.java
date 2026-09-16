package com.shinhan.corebank.otp.adapter.out.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.otp.domain.model.OtpAuthTokenPayload;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

// otpAuthToken의 고객·요청 페이로드와 300초 TTL 및 조건부 소비를 검증한다.
class OtpAuthTokenRedisAdapterTest extends IntegrationTestSupport {

    private static final String KEY_PREFIX = "otp:auth:";

    @Autowired
    OtpAuthTokenRedisAdapter adapter;

    @Autowired
    StringRedisTemplate redisTemplate;

    private String token;

    @AfterEach
    void cleanUp() {
        if (token != null) {
            redisTemplate.delete(KEY_PREFIX + token);
        }
    }

    @Test
    @DisplayName("otpAuthToken을 요청 ID와 고객 ID 페이로드로 300초 동안 저장한다")
    void savesWithFiveMinuteTtl() {
        token = "OTP_AUTH_" + UUID.randomUUID();
        OtpAuthTokenPayload payload = payload("OTP_REQ_test", 1L);

        adapter.save(token, payload, Duration.ofMinutes(5));

        assertThat(adapter.find(token)).contains(payload);
        assertThat(redisTemplate.opsForValue().get(KEY_PREFIX + token))
                .contains("\"otpRequestId\":\"OTP_REQ_test\"")
                .contains("\"customerId\":1");
        assertThat(redisTemplate.getExpire(KEY_PREFIX + token, TimeUnit.SECONDS))
                .isBetween(295L, 300L);
    }

    @Test
    @DisplayName("예상 고객 또는 요청 페이로드가 다르면 토큰을 소진하지 않는다")
    void mismatchDoesNotConsumeToken() {
        token = "OTP_AUTH_" + UUID.randomUUID();
        OtpAuthTokenPayload original = payload("OTP_REQ_original", 1L);
        adapter.save(token, original, Duration.ofMinutes(5));

        assertThat(adapter.consumeIfMatches(token, payload("OTP_REQ_other", 1L)))
                .isFalse();
        assertThat(adapter.consumeIfMatches(token, payload("OTP_REQ_original", 2L)))
                .isFalse();
        assertThat(adapter.find(token)).contains(original);
    }

    @Test
    @DisplayName("일치하는 토큰은 한 번만 소비할 수 있다")
    void consumesMatchingTokenOnce() {
        token = "OTP_AUTH_" + UUID.randomUUID();
        OtpAuthTokenPayload payload = payload("OTP_REQ_test", 1L);
        adapter.save(token, payload, Duration.ofMinutes(5));

        assertThat(adapter.consumeIfMatches(token, payload)).isTrue();
        assertThat(adapter.consumeIfMatches(token, payload)).isFalse();
    }

    private OtpAuthTokenPayload payload(String requestId, Long customerId) {
        return new OtpAuthTokenPayload(requestId, customerId);
    }
}
