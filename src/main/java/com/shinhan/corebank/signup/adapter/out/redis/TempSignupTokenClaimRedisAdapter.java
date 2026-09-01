package com.shinhan.corebank.signup.adapter.out.redis;

import com.shinhan.corebank.signup.application.port.out.TempSignupTokenClaimPort;
import com.shinhan.corebank.signup.domain.model.TempSignupTokenPayload;
import java.util.List;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

// Redis Lua로 tempSignupToken의 선점·완료·실패 복구를 원자적으로 처리한다.
@Component
public class TempSignupTokenClaimRedisAdapter implements TempSignupTokenClaimPort {

    private static final String SOURCE_PREFIX = "signup:temp-signup:";
    private static final String CLAIM_PREFIX = "signup:temp-signup-claim:";
    private static final String OWNER_PREFIX = "signup:temp-signup-owner:";

    private static final DefaultRedisScript<String> CLAIM_SCRIPT = new DefaultRedisScript<>(
            """
                    local value = redis.call('GET', KEYS[1])
                    if not value or redis.call('EXISTS', KEYS[2]) == 1 then
                        return nil
                    end
                    local ttl = redis.call('PTTL', KEYS[1])
                    if ttl <= 0 then
                        return nil
                    end
                    redis.call('SET', KEYS[2], value, 'PX', ttl)
                    redis.call('SET', KEYS[3], ARGV[1], 'PX', ttl)
                    redis.call('DEL', KEYS[1])
                    return value
                    """,
            String.class);

    private static final DefaultRedisScript<Long> COMPLETE_SCRIPT = new DefaultRedisScript<>(
            """
                    if redis.call('GET', KEYS[2]) ~= ARGV[1] then
                        return 0
                    end
                    redis.call('DEL', KEYS[1], KEYS[2])
                    return 1
                    """,
            Long.class);

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            """
                    if redis.call('GET', KEYS[3]) ~= ARGV[1] then
                        return 0
                    end
                    local value = redis.call('GET', KEYS[2])
                    local ttl = redis.call('PTTL', KEYS[2])
                    if not value or ttl <= 0 or redis.call('EXISTS', KEYS[1]) == 1 then
                        return 0
                    end
                    redis.call('SET', KEYS[1], value, 'PX', ttl)
                    redis.call('DEL', KEYS[2], KEYS[3])
                    return 1
                    """,
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public TempSignupTokenClaimRedisAdapter(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<TempSignupTokenPayload> claim(String token, String claimId) {
        if (token == null || token.isBlank() || claimId == null || claimId.isBlank()) {
            return Optional.empty();
        }
        String json = redisTemplate.execute(
                CLAIM_SCRIPT, List.of(sourceKey(token), claimKey(token), ownerKey(token)), claimId);
        return deserialize(json);
    }

    @Override
    public void complete(String token, String claimId) {
        Long completed = redisTemplate.execute(COMPLETE_SCRIPT, List.of(claimKey(token), ownerKey(token)), claimId);
        if (!Long.valueOf(1L).equals(completed)) {
            throw new IllegalStateException("임시 회원가입 토큰 선점 완료에 실패했습니다.");
        }
    }

    @Override
    public void release(String token, String claimId) {
        Long released = redisTemplate.execute(
                RELEASE_SCRIPT, List.of(sourceKey(token), claimKey(token), ownerKey(token)), claimId);
        if (!Long.valueOf(1L).equals(released)) {
            throw new IllegalStateException("임시 회원가입 토큰 선점 복구에 실패했습니다.");
        }
    }

    private Optional<TempSignupTokenPayload> deserialize(String json) {
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, TempSignupTokenPayload.class));
        } catch (JacksonException exception) {
            throw new IllegalStateException("임시 회원가입 토큰 선점값 역직렬화에 실패했습니다.", exception);
        }
    }

    private String sourceKey(String token) {
        return SOURCE_PREFIX + token;
    }

    private String claimKey(String token) {
        return CLAIM_PREFIX + token;
    }

    private String ownerKey(String token) {
        return OWNER_PREFIX + token;
    }
}
