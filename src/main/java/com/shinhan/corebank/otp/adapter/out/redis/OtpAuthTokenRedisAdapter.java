package com.shinhan.corebank.otp.adapter.out.redis;

import com.shinhan.corebank.otp.application.port.out.OtpAuthTokenStorePort;
import com.shinhan.corebank.otp.domain.model.OtpAuthTokenPayload;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

// otpAuthToken의 고객·요청 페이로드를 JSON으로 저장하고 값이 같을 때만 소비한다.
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
    private final ObjectMapper objectMapper;

    public OtpAuthTokenRedisAdapter(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(
            String otpAuthToken,
            OtpAuthTokenPayload payload,
            Duration ttl
    ) {
        redisTemplate.opsForValue().set(key(otpAuthToken), serialize(payload), ttl);
    }

    @Override
    public Optional<OtpAuthTokenPayload> find(String otpAuthToken) {
        String json = redisTemplate.opsForValue().get(key(otpAuthToken));
        return json == null ? Optional.empty() : Optional.of(deserialize(json));
    }

    @Override
    public boolean consumeIfMatches(
            String otpAuthToken,
            OtpAuthTokenPayload expectedPayload
    ) {
        Long result = redisTemplate.execute(
                CONSUME_SCRIPT,
                List.of(key(otpAuthToken)),
                serialize(expectedPayload)
        );
        if (result == null) {
            throw new IllegalStateException("OTP 인증 토큰 소비 결과를 확인할 수 없습니다.");
        }
        return result == SUCCESS;
    }

    private String serialize(OtpAuthTokenPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IllegalStateException("OTP 인증 토큰 직렬화에 실패했습니다.", exception);
        }
    }

    private OtpAuthTokenPayload deserialize(String json) {
        try {
            return objectMapper.readValue(json, OtpAuthTokenPayload.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("OTP 인증 토큰 역직렬화에 실패했습니다.", exception);
        }
    }

    private String key(String otpAuthToken) {
        return KEY_PREFIX + otpAuthToken;
    }
}
