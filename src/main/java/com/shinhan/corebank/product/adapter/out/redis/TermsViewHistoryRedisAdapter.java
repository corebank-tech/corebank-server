package com.shinhan.corebank.product.adapter.out.redis;

import com.shinhan.corebank.common.ephemeralstore.ConditionalOnRedisEphemeralStore;
import com.shinhan.corebank.product.application.port.out.TermsView;
import com.shinhan.corebank.product.application.port.out.TermsViewHistoryPort;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnRedisEphemeralStore
public class TermsViewHistoryRedisAdapter implements TermsViewHistoryPort {

    private static final Duration VIEW_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    public TermsViewHistoryRedisAdapter(StringRedisTemplate redisTemplate, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.clock = clock;
    }

    @Override
    public TermsView record(Long customerId, Long termsId) {
        LocalDateTime viewedAt = LocalDateTime.now(clock);
        redisTemplate.opsForValue().set(key(customerId, termsId), viewedAt.toString(), VIEW_TTL);
        return new TermsView(viewedAt, viewedAt.plus(VIEW_TTL));
    }

    @Override
    public Optional<TermsView> find(Long customerId, Long termsId) {
        String key = key(customerId, termsId);
        String rawViewedAt = redisTemplate.opsForValue().get(key);
        if (rawViewedAt == null) {
            return Optional.empty();
        }
        LocalDateTime viewedAt = LocalDateTime.parse(rawViewedAt);
        Long remainingSeconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (remainingSeconds == null || remainingSeconds <= 0) {
            // GET과 getExpire 사이에 키가 만료된 경우 — 이미 사라진 이력을 유효한 것처럼 반환하지 않는다.
            return Optional.empty();
        }
        return Optional.of(new TermsView(viewedAt, LocalDateTime.now(clock).plusSeconds(remainingSeconds)));
    }

    private String key(Long customerId, Long termsId) {
        return "terms-view:%d:%d".formatted(customerId, termsId);
    }
}
