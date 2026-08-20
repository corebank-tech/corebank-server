package com.shinhan.corebank.signup.adapter.out.redis;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.signup.domain.model.AccountAuthTokenPayload;
import com.shinhan.corebank.signup.domain.model.AgreedTerm;
import com.shinhan.corebank.signup.domain.model.EmailVerificationPurpose;
import com.shinhan.corebank.signup.domain.model.EmailVerificationTokenPayload;
import com.shinhan.corebank.signup.domain.model.TempSignupTokenPayload;
import com.shinhan.corebank.signup.domain.model.TermsAuthTokenPayload;
import com.shinhan.corebank.signup.domain.model.UserIdCheckTokenPayload;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// Redis 토큰 전환의 원자성, TTL, 일회성 소비와 수정 회전을 검증한다.
class SignupTokenTransitionRedisAdapterTest extends IntegrationTestSupport {

    @Autowired TermsAuthTokenRedisAdapter termsAdapter;
    @Autowired AccountAuthTokenRedisAdapter accountAdapter;
    @Autowired UserIdCheckTokenRedisAdapter userIdAdapter;
    @Autowired EmailVerificationTokenRedisAdapter emailAdapter;
    @Autowired TempSignupTokenRedisAdapter tempAdapter;
    @Autowired SignupTokenTransitionRedisAdapter transitionAdapter;
    @Autowired StringRedisTemplate redisTemplate;

    @Test
    void consumesFourTokensAndCreatesTempTokenAtomically() {
        Tokens tokens = saveInitialTokens();
        String temp = "TEMP_SIGNUP_" + UUID.randomUUID();

        boolean result = transitionAdapter.replaceInitialTokensWithTemp(
                tokens.terms(), tokens.account(), tokens.userId(), tokens.email(),
                temp, payload(), Duration.ofMinutes(30)
        );

        assertThat(result).isTrue();
        assertThat(termsAdapter.find(tokens.terms())).isEmpty();
        assertThat(accountAdapter.find(tokens.account())).isEmpty();
        assertThat(userIdAdapter.find(tokens.userId())).isEmpty();
        assertThat(emailAdapter.find(tokens.email())).isEmpty();
        assertThat(tempAdapter.find(temp)).contains(payload());
        assertThat(redisTemplate.getExpire(
                "signup:temp-signup:" + temp,
                TimeUnit.SECONDS
        )).isBetween(1795L, 1800L);
        assertThat(tempAdapter.consume(temp)).contains(payload());
        assertThat(tempAdapter.consume(temp)).isEmpty();
    }

    @Test
    void missingSourceLeavesEveryOtherTokenUntouched() {
        Tokens tokens = saveInitialTokens();
        String missingEmail = "EMAIL_VERIFICATION_missing";

        boolean result = transitionAdapter.replaceInitialTokensWithTemp(
                tokens.terms(), tokens.account(), tokens.userId(), missingEmail,
                "TEMP_SIGNUP_" + UUID.randomUUID(), payload(),
                Duration.ofMinutes(30)
        );

        assertThat(result).isFalse();
        assertThat(termsAdapter.find(tokens.terms())).isPresent();
        assertThat(accountAdapter.find(tokens.account())).isPresent();
        assertThat(userIdAdapter.find(tokens.userId())).isPresent();
    }

    @Test
    void rotatesOldTempAndOnlyAdditionalProofs() {
        String old = "TEMP_SIGNUP_" + UUID.randomUUID();
        String userId = "USER_ID_CHECK_" + UUID.randomUUID();
        String next = "TEMP_SIGNUP_" + UUID.randomUUID();
        tempAdapter.save(old, payload(), Duration.ofMinutes(30));
        userIdAdapter.save(
                userId,
                new UserIdCheckTokenPayload("newuser", LocalDateTime.now()),
                Duration.ofMinutes(3)
        );

        boolean result = transitionAdapter.rotateTempToken(
                old, userId, null, next, payload(), Duration.ofMinutes(30)
        );

        assertThat(result).isTrue();
        assertThat(tempAdapter.find(old)).isEmpty();
        assertThat(userIdAdapter.find(userId)).isEmpty();
        assertThat(tempAdapter.find(next)).isPresent();
    }

    @Test
    void onlyOneConcurrentRotationSucceeds() throws Exception {
        String old = "TEMP_SIGNUP_" + UUID.randomUUID();
        tempAdapter.save(old, payload(), Duration.ofMinutes(30));
        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> first = () -> transitionAdapter.rotateTempToken(
                    old, null, null, "TEMP_SIGNUP_" + UUID.randomUUID(),
                    payload(), Duration.ofMinutes(30)
            );
            Callable<Boolean> second = () -> transitionAdapter.rotateTempToken(
                    old, null, null, "TEMP_SIGNUP_" + UUID.randomUUID(),
                    payload(), Duration.ofMinutes(30)
            );

            var results = executor.invokeAll(List.of(first, second));
            long successCount = 0;
            for (var result : results) {
                if (result.get()) {
                    successCount++;
                }
            }
            assertThat(successCount).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void expiredTempSignupTokenCannotBeFoundOrConsumed()
            throws InterruptedException {
        String token = "TEMP_SIGNUP_" + UUID.randomUUID();
        tempAdapter.save(token, payload(), Duration.ofMillis(100));

        Thread.sleep(200);

        assertThat(tempAdapter.find(token)).isEmpty();
        assertThat(tempAdapter.consume(token)).isEmpty();
    }

    private Tokens saveInitialTokens() {
        String suffix = UUID.randomUUID().toString();
        Tokens tokens = new Tokens(
                "TERMS_AUTH_" + suffix,
                "ACCOUNT_AUTH_" + suffix,
                "USER_ID_CHECK_" + suffix,
                "EMAIL_VERIFICATION_" + suffix
        );
        termsAdapter.save(
                tokens.terms(),
                new TermsAuthTokenPayload(
                        List.of(new AgreedTerm("SIGNUP_TERMS", "1.0")),
                        Instant.parse("2026-08-20T01:00:00Z")
                ),
                Duration.ofMinutes(30)
        );
        accountAdapter.save(
                tokens.account(),
                new AccountAuthTokenPayload(
                        "BANK_CUSTOMER_001", "BANK_ACCOUNT_001",
                        Instant.parse("2026-08-20T01:00:00Z")
                ),
                Duration.ofMinutes(10)
        );
        userIdAdapter.save(
                tokens.userId(),
                new UserIdCheckTokenPayload("honggildong", LocalDateTime.now()),
                Duration.ofMinutes(3)
        );
        emailAdapter.save(
                tokens.email(),
                new EmailVerificationTokenPayload(
                        "hong@corebank.example.com",
                        EmailVerificationPurpose.SIGN_UP,
                        LocalDateTime.now()
                ),
                Duration.ofMinutes(30)
        );
        return tokens;
    }

    private TempSignupTokenPayload payload() {
        return new TempSignupTokenPayload(
                List.of(new AgreedTerm("SIGNUP_TERMS", "1.0")),
                "BANK_CUSTOMER_001", "BANK_ACCOUNT_001", "honggildong",
                "bcrypt-hash", "hong@corebank.example.com", "01012345678",
                Instant.parse("2026-08-20T01:00:00Z")
        );
    }

    // 테스트용 선행 인증 토큰 식별자를 묶어 전달한다.
    private record Tokens(
            String terms,
            String account,
            String userId,
            String email
    ) {
    }
}
