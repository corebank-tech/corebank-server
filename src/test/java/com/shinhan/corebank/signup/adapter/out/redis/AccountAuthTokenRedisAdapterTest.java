package com.shinhan.corebank.signup.adapter.out.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.signup.domain.model.AccountAuthTokenPayload;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

// accountAuthToken의 TTL, 최소 payload와 일회성 소비를 검증한다.
class AccountAuthTokenRedisAdapterTest extends IntegrationTestSupport {

    private static final String KEY_PREFIX = "signup:account-auth:";

    @Autowired
    AccountAuthTokenRedisAdapter adapter;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("accountAuthToken은 TTL 600초이고 한 번만 소비된다")
    void accountAuthTokenHasTtlAndIsSingleUse() {
        String token = "ACCOUNT_AUTH_" + UUID.randomUUID();
        AccountAuthTokenPayload payload = payload();

        adapter.save(token, payload, Duration.ofMinutes(10));

        assertThat(redisTemplate.getExpire(KEY_PREFIX + token, TimeUnit.SECONDS))
                .isBetween(595L, 600L);
        assertThat(adapter.consume(token)).contains(payload);
        assertThat(adapter.consume(token)).isEmpty();
    }

    @Test
    @DisplayName("Redis payload에 전체 계좌번호와 계좌비밀번호가 저장되지 않는다")
    void payloadDoesNotContainSensitiveAccountData() {
        String token = "ACCOUNT_AUTH_" + UUID.randomUUID();

        adapter.save(token, payload(), Duration.ofMinutes(10));

        String storedJson = redisTemplate.opsForValue().get(KEY_PREFIX + token);
        assertThat(storedJson)
                .contains("BANK_CUSTOMER_001", "BANK_ACCOUNT_001")
                .doesNotContain("110123456789", "1234", "accountPassword");
    }

    @Test
    @DisplayName("만료된 accountAuthToken은 소비할 수 없다")
    void expiredTokenCannotBeConsumed() throws InterruptedException {
        String token = "ACCOUNT_AUTH_" + UUID.randomUUID();
        adapter.save(token, payload(), Duration.ofMillis(100));

        Thread.sleep(200);

        assertThat(adapter.consume(token)).isEmpty();
    }

    private AccountAuthTokenPayload payload() {
        return new AccountAuthTokenPayload(
                "BANK_CUSTOMER_001", "BANK_ACCOUNT_001", Instant.parse("2026-08-20T01:00:00Z"));
    }
}
