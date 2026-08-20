package com.shinhan.corebank.signup.adapter.out.redis;

import com.shinhan.corebank.signup.application.port.out.TempSignupTokenPort;
import com.shinhan.corebank.signup.domain.model.TempSignupTokenPayload;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

// tempSignupToken을 Redis에 저장하고 조회하거나 원자적으로 소비한다.
@Component
public class TempSignupTokenRedisAdapter implements TempSignupTokenPort {

    static final String KEY_PREFIX = "signup:temp-signup:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public TempSignupTokenRedisAdapter(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(String token, TempSignupTokenPayload payload, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(
                    key(token),
                    objectMapper.writeValueAsString(payload),
                    ttl
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "임시 회원가입 토큰 직렬화에 실패했습니다.",
                    exception
            );
        }
    }

    @Override
    public Optional<TempSignupTokenPayload> find(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return deserialize(redisTemplate.opsForValue().get(key(token)));
    }

    @Override
    public Optional<TempSignupTokenPayload> consume(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return deserialize(redisTemplate.opsForValue().getAndDelete(key(token)));
    }

    private Optional<TempSignupTokenPayload> deserialize(String json) {
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(
                    json,
                    TempSignupTokenPayload.class
            ));
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "임시 회원가입 토큰 역직렬화에 실패했습니다.",
                    exception
            );
        }
    }

    private String key(String token) {
        return KEY_PREFIX + token;
    }
}
