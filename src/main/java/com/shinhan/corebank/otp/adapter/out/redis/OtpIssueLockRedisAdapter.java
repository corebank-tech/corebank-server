package com.shinhan.corebank.otp.adapter.out.redis;

import com.shinhan.corebank.otp.application.port.out.OtpIssueLockPort;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

// 고객별 Redis 잠금으로 동시 OTP 발급 트랜잭션의 진입 순서를 보장한다.
@Component
@RequiredArgsConstructor
public class OtpIssueLockRedisAdapter implements OtpIssueLockPort {

    private static final String KEY_PREFIX = "otp:issue-lock:";
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            """
                    if redis.call('GET', KEYS[1]) ~= ARGV[1] then
                        return 0
                    end
                    redis.call('DEL', KEYS[1])
                    return 1
                    """,
            Long.class);

    private final StringRedisTemplate redisTemplate;

    @Override
    public Optional<String> tryAcquire(Long customerId, Duration ttl) {
        String ownerId = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key(customerId), ownerId, ttl);
        if (acquired == null) {
            throw new IllegalStateException("OTP 발급 잠금 결과를 확인할 수 없습니다.");
        }
        return acquired ? Optional.of(ownerId) : Optional.empty();
    }

    @Override
    public void release(Long customerId, String ownerId) {
        Long result = redisTemplate.execute(RELEASE_SCRIPT, List.of(key(customerId)), ownerId);
        if (result == null) {
            throw new IllegalStateException("OTP 발급 잠금 해제 결과를 확인할 수 없습니다.");
        }
        // 소유권이 이미 만료됐거나 바뀐 잠금은 삭제하지 않는다.
    }

    private String key(Long customerId) {
        return KEY_PREFIX + customerId;
    }
}
