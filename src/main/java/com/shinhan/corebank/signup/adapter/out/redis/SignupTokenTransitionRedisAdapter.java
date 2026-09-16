package com.shinhan.corebank.signup.adapter.out.redis;

import com.shinhan.corebank.signup.application.port.out.SignupTokenTransitionPort;
import com.shinhan.corebank.signup.domain.model.TempSignupTokenPayload;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

// Redis Lua로 선행 인증 토큰 소비와 tempSignupToken 저장을 원자적으로 처리한다.
@Component
public class SignupTokenTransitionRedisAdapter implements SignupTokenTransitionPort {

    private static final String TERMS_PREFIX = "signup:terms-auth:";
    private static final String ACCOUNT_PREFIX = "signup:account-auth:";
    private static final String USER_ID_PREFIX = "signup:user-id-check:";
    private static final String EMAIL_PREFIX = "signup:email-verification:";
    private static final String TEMP_PREFIX = "signup:temp-signup:";
    private static final long SUCCESS = 1L;
    private static final long DESTINATION_ALREADY_EXISTS = -1L;

    private static final DefaultRedisScript<Long> TRANSITION_SCRIPT = new DefaultRedisScript<>(
            """
                    for i = 1, #KEYS - 1 do
                        if redis.call('EXISTS', KEYS[i]) == 0 then
                            return 0
                        end
                    end
                    if redis.call('EXISTS', KEYS[#KEYS]) == 1 then
                        return -1
                    end
                    redis.call('SET', KEYS[#KEYS], ARGV[1], 'PX', ARGV[2])
                    for i = 1, #KEYS - 1 do
                        redis.call('DEL', KEYS[i])
                    end
                    return 1
                    """,
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public SignupTokenTransitionRedisAdapter(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean replaceInitialTokensWithTemp(
            String termsAuthToken,
            String accountAuthToken,
            String userIdCheckToken,
            String emailVerificationToken,
            String newTempSignupToken,
            TempSignupTokenPayload payload,
            Duration ttl) {
        return transition(
                List.of(
                        TERMS_PREFIX + termsAuthToken,
                        ACCOUNT_PREFIX + accountAuthToken,
                        USER_ID_PREFIX + userIdCheckToken,
                        EMAIL_PREFIX + emailVerificationToken),
                newTempSignupToken,
                payload,
                ttl);
    }

    @Override
    public boolean rotateTempToken(
            String currentTempSignupToken,
            String userIdCheckToken,
            String emailVerificationToken,
            String newTempSignupToken,
            TempSignupTokenPayload payload,
            Duration ttl) {
        List<String> sourceKeys = new ArrayList<>();
        sourceKeys.add(TEMP_PREFIX + currentTempSignupToken);
        if (hasText(userIdCheckToken)) {
            sourceKeys.add(USER_ID_PREFIX + userIdCheckToken);
        }
        if (hasText(emailVerificationToken)) {
            sourceKeys.add(EMAIL_PREFIX + emailVerificationToken);
        }

        return transition(sourceKeys, newTempSignupToken, payload, ttl);
    }

    private boolean transition(
            List<String> sourceKeys, String newTempSignupToken, TempSignupTokenPayload payload, Duration ttl) {
        List<String> keys = new ArrayList<>(sourceKeys);
        keys.add(TEMP_PREFIX + newTempSignupToken);

        Long result = redisTemplate.execute(TRANSITION_SCRIPT, keys, serialize(payload), Long.toString(ttl.toMillis()));

        if (result == null) {
            throw new IllegalStateException("Redis 토큰 전환 결과를 확인할 수 없습니다.");
        }
        if (result == DESTINATION_ALREADY_EXISTS) {
            throw new IllegalStateException("임시 회원가입 토큰이 충돌했습니다.");
        }
        return result == SUCCESS;
    }

    private String serialize(TempSignupTokenPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IllegalStateException("임시 회원가입 토큰 직렬화에 실패했습니다.", exception);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
