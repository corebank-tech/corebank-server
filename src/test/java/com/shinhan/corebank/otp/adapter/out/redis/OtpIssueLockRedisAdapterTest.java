package com.shinhan.corebank.otp.adapter.out.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

// 고객별 OTP 발급 잠금의 배타성과 소유자 조건부 해제를 검증한다.
class OtpIssueLockRedisAdapterTest extends IntegrationTestSupport {

    private static final Long CUSTOMER_ID = 9_999_999L;
    private static final String KEY = "otp:issue-lock:" + CUSTOMER_ID;

    @Autowired
    OtpIssueLockRedisAdapter adapter;

    @Autowired
    StringRedisTemplate redisTemplate;

    @AfterEach
    void cleanUp() {
        redisTemplate.delete(KEY);
    }

    @Test
    @DisplayName("같은 고객의 잠금은 한 소유자만 획득하고 다른 소유자는 해제하지 못한다")
    void locksPerCustomerAndReleasesOnlyByOwner() {
        String ownerId = adapter.tryAcquire(CUSTOMER_ID, Duration.ofSeconds(30)).orElseThrow();

        assertThat(adapter.tryAcquire(CUSTOMER_ID, Duration.ofSeconds(30))).isEmpty();
        adapter.release(CUSTOMER_ID, "other-owner");
        assertThat(adapter.tryAcquire(CUSTOMER_ID, Duration.ofSeconds(30))).isEmpty();

        adapter.release(CUSTOMER_ID, ownerId);
        assertThat(adapter.tryAcquire(CUSTOMER_ID, Duration.ofSeconds(30))).isPresent();
    }
}
