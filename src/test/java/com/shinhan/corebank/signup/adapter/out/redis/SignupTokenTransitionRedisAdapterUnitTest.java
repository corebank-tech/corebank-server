package com.shinhan.corebank.signup.adapter.out.redis;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

import com.shinhan.corebank.signup.domain.model.TempSignupTokenPayload;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class SignupTokenTransitionRedisAdapterUnitTest {

    @Mock
    StringRedisTemplate redisTemplate;

    @Mock
    ObjectMapper objectMapper;

    SignupTokenTransitionRedisAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SignupTokenTransitionRedisAdapter(
                redisTemplate,
                objectMapper
        );
    }

    @Test
    @DisplayName("Lua 실행 결과가 null이면 정상적인 토큰 부재와 구분해 내부 오류로 처리한다")
    void rejectsNullScriptResult() throws Exception {
        given(objectMapper.writeValueAsString(any())).willReturn("{}");
        given(redisTemplate.execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                anyList(),
                any(),
                any()
        )).willReturn(null);

        assertThatThrownBy(() -> adapter.rotateTempToken(
                "TEMP_SIGNUP_old",
                null,
                null,
                "TEMP_SIGNUP_new",
                payload(),
                Duration.ofMinutes(30)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Redis 토큰 전환 결과를 확인할 수 없습니다.");
    }

    private TempSignupTokenPayload payload() {
        return new TempSignupTokenPayload(
                List.of(),
                "BANK_CUSTOMER_001",
                "BANK_ACCOUNT_001",
                "honggildong",
                "bcrypt-hash",
                "hong@corebank.example.com",
                "01012345678",
                Instant.parse("2026-08-20T01:00:00Z")
        );
    }
}
