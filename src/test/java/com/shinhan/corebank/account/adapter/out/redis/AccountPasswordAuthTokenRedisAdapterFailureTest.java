package com.shinhan.corebank.account.adapter.out.redis;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.shinhan.corebank.account.domain.AccountPasswordAuthTokenPayload;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@DisplayName("계좌비밀번호 인증 토큰 Redis 장애 단위 테스트")
class AccountPasswordAuthTokenRedisAdapterFailureTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ObjectMapper objectMapper = mock(ObjectMapper.class);
    private AccountPasswordAuthTokenRedisAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AccountPasswordAuthTokenRedisAdapter(redisTemplate, objectMapper);
    }

    @Test
    @DisplayName("고객·계좌 기준 Redis 소비 결과가 없으면 장애로 처리한다")
    void rejectsMissingAccountConsumeResult() throws Exception {
        AccountPasswordAuthTokenPayload payload = new AccountPasswordAuthTokenPayload(1L, 101L);
        given(objectMapper.writeValueAsString(payload)).willReturn("payload");
        given(redisTemplate.execute(
                        ArgumentMatchers.<RedisScript<Long>>any(), anyList(), ArgumentMatchers.any(String.class)))
                .willReturn(null);

        assertThatThrownBy(() -> adapter.consumeIfMatches("token", payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("계좌비밀번호 인증 토큰 소비 결과를 확인할 수 없습니다.");
    }

    @Test
    @DisplayName("고객 기준 Redis 소비 결과가 없으면 장애로 처리한다")
    void rejectsMissingCustomerConsumeResult() {
        given(redisTemplate.execute(
                        ArgumentMatchers.<RedisScript<Long>>any(), anyList(), ArgumentMatchers.any(String.class)))
                .willReturn(null);

        assertThatThrownBy(() -> adapter.consumeIfCustomerMatches("token", 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("계좌비밀번호 인증 토큰 고객 기준 소비 결과를 확인할 수 없습니다.");
    }

    @Test
    @DisplayName("payload 직렬화 실패를 Redis 저장 장애로 변환한다")
    void convertsSerializationFailure() throws Exception {
        AccountPasswordAuthTokenPayload payload = new AccountPasswordAuthTokenPayload(1L, 101L);
        JacksonException cause = mock(JacksonException.class);
        given(objectMapper.writeValueAsString(payload)).willThrow(cause);

        assertThatThrownBy(() -> adapter.save("token", payload, Duration.ofMinutes(5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("계좌비밀번호 인증 토큰 직렬화에 실패했습니다.")
                .hasCause(cause);
    }
}
