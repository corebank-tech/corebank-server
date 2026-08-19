package com.shinhan.corebank.signup.adapter.out.redis;

import com.shinhan.corebank.signup.application.port.out.TermsAuthTokenPort;
import com.shinhan.corebank.signup.domain.model.TermsAuthTokenPayload;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

@Component
public class TermsAuthTokenRedisAdapter implements TermsAuthTokenPort {

    private static final String KEY_PREFIX = "signup:terms-auth:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public TermsAuthTokenRedisAdapter(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(
            String termsAuthToken,
            TermsAuthTokenPayload payload,
            Duration ttl
    ) {
        try {
            String json = objectMapper.writeValueAsString(payload);

            redisTemplate.opsForValue().set(
                    key(termsAuthToken),
                    json,
                    ttl
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "약관 인증 토큰 저장값 직렬화에 실패했습니다.",
                    exception
            );
        }
    }

    @Override
    public Optional<TermsAuthTokenPayload> consume(
            String termsAuthToken
    ) {
        String json = redisTemplate.opsForValue()
                .getAndDelete(key(termsAuthToken));

        if (json == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(
                    objectMapper.readValue(
                            json,
                            TermsAuthTokenPayload.class
                    )
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "약관 인증 토큰 저장값 역직렬화에 실패했습니다.",
                    exception
            );
        }
    }

    private String key(String termsAuthToken) {
        return KEY_PREFIX + termsAuthToken;
    }
}
