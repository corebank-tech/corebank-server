package com.shinhan.corebank.product.adapter.out.redis;

import com.shinhan.corebank.product.application.port.out.TermsView;
import com.shinhan.corebank.product.application.port.out.TermsViewHistoryPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Component
public class TermsViewHistoryRedisAdapter implements TermsViewHistoryPort {

    private static final Duration VIEW_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;

    public TermsViewHistoryRedisAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public TermsView record(Long customerId, Long termsId) {
        LocalDateTime viewedAt = LocalDateTime.now();
        redisTemplate.opsForValue().set(key(customerId, termsId), viewedAt.toString(), VIEW_TTL);
        return new TermsView(viewedAt, viewedAt.plus(VIEW_TTL));
    }

    @Override
    public Optional<TermsView> find(Long customerId, Long termsId) {
        String rawViewedAt = redisTemplate.opsForValue().get(key(customerId, termsId));
        if (rawViewedAt == null) {
            return Optional.empty();
        }
        LocalDateTime viewedAt = LocalDateTime.parse(rawViewedAt);
        return Optional.of(new TermsView(viewedAt, viewedAt.plus(VIEW_TTL)));
    }

    private String key(Long customerId, Long termsId) {
        return "terms-view:%d:%d".formatted(customerId, termsId);
    }
}
