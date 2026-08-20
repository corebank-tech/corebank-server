package com.shinhan.corebank.otp.adapter.out.redis;

import com.shinhan.corebank.otp.application.port.out.OtpAuthTokenStorePort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

// otpAuthToken이 예상 OTP 요청과 연결된 경우에만 Redis에서 원자적으로 소비한다.
@Component
public class OtpAuthTokenRedisAdapter implements OtpAuthTokenStorePort {

    private static final String KEY_PREFIX = "otp:auth:";
    private static final long SUCCESS = 1L;

    private static final DefaultRedisScript<Long> CONSUME_SCRIPT =
            new DefaultRedisScript<>("""
                    local value = redis.call('GET', KEYS[1])
                    if not value then
                        return 0
                    end
                    if value ~= ARGV[1] then
                        return -1
                    end
                    redis.call('DEL', KEYS[1])
                    return 1
                    """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public OtpAuthTokenRedisAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(String otpAuthToken, String otpRequestId, Duration ttl) {
        redisTemplate.opsForValue().set(key(otpAuthToken), otpRequestId, ttl);
    }

    @Override
    public Optional<String> findRequestId(String otpAuthToken) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(otpAuthToken)));
    }

    @Override
    public boolean consumeIfMatches(String otpAuthToken, String expectedOtpRequestId) {
        Long result = redisTemplate.execute(
                CONSUME_SCRIPT,
                List.of(key(otpAuthToken)),
                expectedOtpRequestId
        );
        if (result == null) {
            throw new IllegalStateException("OTP 인증 토큰 소비 결과를 확인할 수 없습니다.");
        }
        return result == SUCCESS;
    }

    private String key(String otpAuthToken) {
        return KEY_PREFIX + otpAuthToken;
    }
}
