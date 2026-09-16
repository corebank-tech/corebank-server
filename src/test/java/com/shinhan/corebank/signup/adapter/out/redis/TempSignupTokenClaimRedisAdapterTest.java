package com.shinhan.corebank.signup.adapter.out.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.signup.domain.model.AgreedTerm;
import com.shinhan.corebank.signup.domain.model.TempSignupTokenPayload;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// tempSignupToken의 Redis 선점·완료·실패 복구와 일회성을 검증한다.
class TempSignupTokenClaimRedisAdapterTest extends IntegrationTestSupport {

    @Autowired
    TempSignupTokenRedisAdapter tempTokenAdapter;

    @Autowired
    TempSignupTokenClaimRedisAdapter claimAdapter;

    @Test
    void claimsOnlyOnceAndCompletesPermanently() {
        String token = token();
        tempTokenAdapter.save(token, payload(), Duration.ofMinutes(30));

        assertThat(claimAdapter.claim(token, "claim-1")).contains(payload());
        assertThat(claimAdapter.claim(token, "claim-2")).isEmpty();

        claimAdapter.complete(token, "claim-1");
        assertThat(tempTokenAdapter.find(token)).isEmpty();
        assertThat(claimAdapter.claim(token, "claim-3")).isEmpty();
    }

    @Test
    void restoresTokenWithRemainingTtlAfterFailure() {
        String token = token();
        tempTokenAdapter.save(token, payload(), Duration.ofMinutes(30));

        assertThat(claimAdapter.claim(token, "claim-1")).isPresent();
        claimAdapter.release(token, "claim-1");

        assertThat(tempTokenAdapter.find(token)).contains(payload());
        assertThat(claimAdapter.claim(token, "claim-2")).isPresent();
    }

    @Test
    void concurrentClaimsAllowOnlyOneOwner() throws Exception {
        String token = token();
        tempTokenAdapter.save(token, payload(), Duration.ofMinutes(30));
        List<Callable<Optional<TempSignupTokenPayload>>> attempts =
                List.of(() -> claimAdapter.claim(token, "claim-1"), () -> claimAdapter.claim(token, "claim-2"));

        List<Optional<TempSignupTokenPayload>> results;
        try (var executor = Executors.newFixedThreadPool(2)) {
            results = executor.invokeAll(attempts).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception exception) {
                            throw new AssertionError(exception);
                        }
                    })
                    .toList();
        }

        assertThat(results).filteredOn(Optional::isPresent).hasSize(1);
    }

    private String token() {
        return "TEMP_SIGNUP_" + UUID.randomUUID();
    }

    private TempSignupTokenPayload payload() {
        return new TempSignupTokenPayload(
                List.of(new AgreedTerm("1", "1.0")),
                "BANK_CUSTOMER_001",
                "BANK_ACCOUNT_001",
                "honggildong",
                "$2y$10$hash",
                "hong@corebank.example.com",
                "01012345678",
                Instant.parse("2026-08-20T01:00:00Z"));
    }
}
