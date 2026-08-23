package com.shinhan.corebank.account.adapter.out.redis;

import com.shinhan.corebank.account.application.port.out.AccountPasswordAuthTokenStorePort;
import com.shinhan.corebank.account.domain.AccountPasswordAuthTokenPayload;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;

// 계좌비밀번호 인증 토큰의 고객·계좌 payload를 Redis에 저장한다.
@Component
public class AccountPasswordAuthTokenRedisAdapter
        implements AccountPasswordAuthTokenStorePort {

    private static final String KEY_PREFIX =
            "account:password:auth:";
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
    private final ObjectMapper objectMapper;

    public AccountPasswordAuthTokenRedisAdapter(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(
            String token,
            AccountPasswordAuthTokenPayload payload,
            Duration ttl
    ) {
        redisTemplate.opsForValue().set(
                key(token),
                serialize(payload),
                ttl
        );
    }

    @Override
    public boolean consumeIfMatches(
            String token,
            AccountPasswordAuthTokenPayload expectedPayload
    ) {
        Long result = redisTemplate.execute(
                CONSUME_SCRIPT,
                List.of(key(token)),
                serialize(expectedPayload)
        );

        if (result == null) {
            throw new IllegalStateException(
                    "계좌비밀번호 인증 토큰 소비 결과를 확인할 수 없습니다."
            );
        }

        return result == SUCCESS;
    }

    // Redis에 토큰 원문과 payload JSON만 저장하도록 직렬화한다.
    private String serialize(
            AccountPasswordAuthTokenPayload payload
    ) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "계좌비밀번호 인증 토큰 직렬화에 실패했습니다.",
                    exception
            );
        }
    }

    private String key(String token) {
        return KEY_PREFIX + token;
    }
}
