package com.shinhan.corebank.product.adapter.out.redis;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.product.application.port.out.TermsView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TermsViewHistoryRedisAdapterTest extends IntegrationTestSupport {

    @Autowired
    TermsViewHistoryRedisAdapter adapter;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("record 후 find하면 같은 열람 이력을 반환한다")
    void recordThenFind() {
        TermsView recorded = adapter.record(1L, 301L);

        Optional<TermsView> found = adapter.find(1L, 301L);

        assertThat(found).isPresent();
        assertThat(found.get().viewedAt()).isEqualTo(recorded.viewedAt());
    }

    @Test
    @DisplayName("기록된 적 없는 조합이면 빈 Optional을 반환한다")
    void find_notRecorded() {
        assertThat(adapter.find(999L, 999L)).isEmpty();
    }

    @Test
    @DisplayName("TTL이 30분으로 설정된다")
    void record_setsTtl() {
        adapter.record(2L, 302L);

        Long ttlSeconds = redisTemplate.getExpire("terms-view:2:302", java.util.concurrent.TimeUnit.SECONDS);

        long expectedSeconds = Duration.ofMinutes(30).toSeconds();
        assertThat(ttlSeconds).isBetween(expectedSeconds - 5, expectedSeconds);
    }
}
