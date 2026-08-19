package com.shinhan.corebank.signup.adapter.out.redis;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.signup.domain.model.EmailVerificationPurpose;
import com.shinhan.corebank.signup.domain.model.EmailVerificationTokenPayload;
import com.shinhan.corebank.signup.domain.model.UserIdCheckTokenPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SignupIdentityTokenRedisAdapterTest extends IntegrationTestSupport {

    @Autowired UserIdCheckTokenRedisAdapter userIdTokenAdapter;
    @Autowired EmailVerificationTokenRedisAdapter emailTokenAdapter;
    @Autowired StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("아이디 중복확인 토큰은 TTL 180초이고 한 번만 소비된다")
    void userIdTokenHasTtlAndIsSingleUse() {
        String token = "USER_ID_CHECK_" + UUID.randomUUID();
        UserIdCheckTokenPayload payload = new UserIdCheckTokenPayload(
                "user1234",
                LocalDateTime.of(2026, 8, 19, 10, 0)
        );

        userIdTokenAdapter.save(token, payload, Duration.ofMinutes(3));

        assertThat(redisTemplate.getExpire(
                "signup:user-id-check:" + token,
                TimeUnit.SECONDS
        )).isBetween(175L, 180L);
        assertThat(userIdTokenAdapter.consume(token)).contains(payload);
        assertThat(userIdTokenAdapter.consume(token)).isEmpty();
    }

    @Test
    @DisplayName("이메일 인증 토큰은 TTL 1800초이고 한 번만 소비된다")
    void emailTokenHasTtlAndIsSingleUse() {
        String token = "EMAIL_VERIFICATION_" + UUID.randomUUID();
        EmailVerificationTokenPayload payload =
                new EmailVerificationTokenPayload(
                        "user@example.com",
                        EmailVerificationPurpose.SIGN_UP,
                        LocalDateTime.of(2026, 8, 19, 10, 0)
                );

        emailTokenAdapter.save(token, payload, Duration.ofMinutes(30));

        assertThat(redisTemplate.getExpire(
                "signup:email-verification:" + token,
                TimeUnit.SECONDS
        )).isBetween(1795L, 1800L);
        assertThat(emailTokenAdapter.consume(token)).contains(payload);
        assertThat(emailTokenAdapter.consume(token)).isEmpty();
    }
}
