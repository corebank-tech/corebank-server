package com.shinhan.corebank.signup.adapter.out.redis;

import com.shinhan.corebank.signup.application.port.out.EmailVerificationTokenPort;
import com.shinhan.corebank.signup.domain.model.EmailVerificationTokenPayload;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

// 이메일 인증 완료 토큰을 Redis에서 원자적으로 저장·소비한다.
@Component
public class EmailVerificationTokenRedisAdapter
        implements EmailVerificationTokenPort {

    private static final String KEY_PREFIX =
            "signup:email-verification:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public EmailVerificationTokenRedisAdapter(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(
            String token,
            EmailVerificationTokenPayload payload,
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
                    "이메일 인증 토큰 직렬화에 실패했습니다.",
                    exception
            );
        }
    }

    @Override
    public Optional<EmailVerificationTokenPayload> find(String token) {
        return deserialize(redisTemplate.opsForValue().get(key(token)));
    }

    @Override
    public Optional<EmailVerificationTokenPayload> consume(String token) {
        return deserialize(redisTemplate.opsForValue().getAndDelete(key(token)));
    }

    private Optional<EmailVerificationTokenPayload> deserialize(String json) {
        if (json == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(
                    json,
                    EmailVerificationTokenPayload.class
            ));
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "이메일 인증 토큰 역직렬화에 실패했습니다.",
                    exception
            );
        }
    }

    private String key(String token) {
        return KEY_PREFIX + token;
    }
}
