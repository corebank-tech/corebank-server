package com.shinhan.corebank.signup.adapter.out.redis;

import com.shinhan.corebank.signup.application.port.out.AccountAuthTokenPort;
import com.shinhan.corebank.signup.domain.model.AccountAuthTokenPayload;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

// accountAuthToken을 Redis에 저장하고 원자적으로 소비한다.
@Component
public class AccountAuthTokenRedisAdapter implements AccountAuthTokenPort {

    private static final String KEY_PREFIX = "signup:account-auth:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public AccountAuthTokenRedisAdapter(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(
            String token,
            AccountAuthTokenPayload payload,
            Duration ttl
    ) {
        try {
            redisTemplate.opsForValue().set(
                    key(token),
                    objectMapper.writeValueAsString(payload),
                    ttl
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "계좌 인증 토큰 직렬화에 실패했습니다.",
                    exception
            );
        }
    }

    @Override
    public Optional<AccountAuthTokenPayload> consume(String token) {
        String json = redisTemplate.opsForValue().getAndDelete(key(token));
        if (json == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(
                    json,
                    AccountAuthTokenPayload.class
            ));
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "계좌 인증 토큰 역직렬화에 실패했습니다.",
                    exception
            );
        }
    }

    private String key(String token) {
        return KEY_PREFIX + token;
    }
}
