package com.shinhan.corebank.signup.adapter.out.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.signup.domain.model.AgreedTerm;
import com.shinhan.corebank.signup.domain.model.TermsAuthTokenPayload;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

class TermsAuthTokenRedisAdapterTest extends IntegrationTestSupport {

    private static final Duration TERMS_AUTH_TTL = Duration.ofMinutes(30);
    private static final String KEY_PREFIX = "signup:terms-auth:";

    @Autowired
    TermsAuthTokenRedisAdapter adapter;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("저장된 termsAuthToken의 TTL은 1800초다")
    void saveSetsThirtyMinuteTtl() {
        String token = token();

        adapter.save(token, payload(), TERMS_AUTH_TTL);

        Long ttlSeconds = redisTemplate.getExpire(KEY_PREFIX + token, TimeUnit.SECONDS);

        assertThat(ttlSeconds).isBetween(1795L, 1800L);
    }

    @Test
    @DisplayName("저장한 termsAuthToken을 소비하면 원래 payload를 반환한다")
    void saveThenConsumeReturnsPayload() {
        String token = token();
        TermsAuthTokenPayload payload = payload();
        adapter.save(token, payload, TERMS_AUTH_TTL);

        Optional<TermsAuthTokenPayload> result = adapter.consume(token);

        assertThat(result).contains(payload);
    }

    @Test
    @DisplayName("consume한 termsAuthToken은 다시 사용할 수 없다")
    void consumedTokenCannotBeReused() {
        String token = token();
        adapter.save(token, payload(), TERMS_AUTH_TTL);

        assertThat(adapter.consume(token)).isPresent();
        assertThat(adapter.consume(token)).isEmpty();
    }

    @Test
    @DisplayName("만료된 termsAuthToken은 소비할 수 없다")
    void expiredTokenCannotBeConsumed() throws InterruptedException {
        String token = token();
        adapter.save(token, payload(), Duration.ofMillis(100));

        Thread.sleep(200);

        assertThat(adapter.consume(token)).isEmpty();
    }

    private String token() {
        return "TERMS_AUTH_" + UUID.randomUUID();
    }

    private TermsAuthTokenPayload payload() {
        return new TermsAuthTokenPayload(
                List.of(new AgreedTerm("1", "v1.0"), new AgreedTerm("2", "v1.0")),
                Instant.parse("2026-08-19T00:00:00Z"));
    }
}
