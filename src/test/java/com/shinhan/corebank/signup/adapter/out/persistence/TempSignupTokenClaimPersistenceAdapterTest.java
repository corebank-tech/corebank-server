package com.shinhan.corebank.signup.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.authtoken.AuthTokenTestRows;
import com.shinhan.corebank.signup.domain.model.AgreedTerm;
import com.shinhan.corebank.signup.domain.model.TempSignupTokenPayload;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

// 가입 완료용 임시 토큰 선점 → 완료/복구 흐름과 실패 시 예외를 검증한다.
class TempSignupTokenClaimPersistenceAdapterTest extends IntegrationTestSupport {

    private static final Duration TTL = Duration.ofMinutes(30);

    @Autowired
    TempSignupTokenClaimPersistenceAdapter adapter;

    @Autowired
    TempSignupTokenPersistenceAdapter tempSignupTokenAdapter;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private final String token = "SIGNUP_TEST_" + UUID.randomUUID();
    private final TempSignupTokenPayload payload = new TempSignupTokenPayload(
            List.of(new AgreedTerm("T1", "1.0")),
            "EBC-1",
            "EBA-1",
            "tester01",
            "$2a$10$hash",
            "tester@example.com",
            "01012345678",
            Instant.parse("2026-10-09T00:00:00Z"));

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM auth_token WHERE token_hash = ?", AuthTokenTestRows.hashOf(token));
    }

    @Test
    @DisplayName("한 번만 선점되고, 완료하면 다시 선점·조회할 수 없으며, 완료 뒤 복구는 실패한다")
    void claimThenComplete() {
        tempSignupTokenAdapter.save(token, payload, TTL);
        String claimId = UUID.randomUUID().toString();

        assertThat(adapter.claim(token, claimId)).contains(payload);
        assertThat(adapter.claim(token, UUID.randomUUID().toString())).isEmpty();

        adapter.complete(token, claimId);

        assertThat(tempSignupTokenAdapter.find(token)).isEmpty();
        assertThatThrownBy(() -> adapter.release(token, claimId)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("가입 실패로 복구하면 같은 토큰을 다시 선점할 수 있다")
    void claimThenRelease() {
        tempSignupTokenAdapter.save(token, payload, TTL);
        String claimId = UUID.randomUUID().toString();
        adapter.claim(token, claimId);

        adapter.release(token, claimId);

        assertThat(tempSignupTokenAdapter.find(token)).contains(payload);
        assertThat(adapter.claim(token, UUID.randomUUID().toString())).contains(payload);
    }

    @Test
    @DisplayName("다른 claimId로 완료하거나 빈 값으로 선점하면 실패한다")
    void wrongClaimIdFails() {
        tempSignupTokenAdapter.save(token, payload, TTL);
        adapter.claim(token, UUID.randomUUID().toString());

        assertThatThrownBy(() -> adapter.complete(token, "other-claim")).isInstanceOf(IllegalStateException.class);
        assertThat(adapter.claim(token, " ")).isEmpty();
    }
}
