package com.shinhan.corebank.signup.adapter.out.redis;

import com.shinhan.corebank.signup.application.port.out.UserIdCheckTokenPort;
import com.shinhan.corebank.signup.domain.model.UserIdCheckTokenPayload;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

// 아이디 중복확인 토큰을 Redis에서 원자적으로 저장·소비한다.
@Component
public class UserIdCheckTokenRedisAdapter
        implements UserIdCheckTokenPort {

    private static final String KEY_PREFIX = "signup:user-id-check:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public UserIdCheckTokenRedisAdapter(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(
            String token,
            UserIdCheckTokenPayload payload,
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
                    "아이디 중복확인 토큰 직렬화에 실패했습니다.",
                    exception
            );
        }
    }

    @Override
    public Optional<UserIdCheckTokenPayload> consume(String token) {
        String json = redisTemplate.opsForValue().getAndDelete(key(token));
        if (json == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(
                    json,
                    UserIdCheckTokenPayload.class
            ));
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "아이디 중복확인 토큰 역직렬화에 실패했습니다.",
                    exception
            );
        }
    }

    private String key(String token) {
        return KEY_PREFIX + token;
    }
}
