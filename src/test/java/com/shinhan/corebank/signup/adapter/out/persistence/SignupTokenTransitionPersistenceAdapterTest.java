package com.shinhan.corebank.signup.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.authtoken.AuthTokenTestRows;
import com.shinhan.corebank.signup.domain.model.AccountAuthTokenPayload;
import com.shinhan.corebank.signup.domain.model.AgreedTerm;
import com.shinhan.corebank.signup.domain.model.EmailVerificationPurpose;
import com.shinhan.corebank.signup.domain.model.EmailVerificationTokenPayload;
import com.shinhan.corebank.signup.domain.model.TempSignupTokenPayload;
import com.shinhan.corebank.signup.domain.model.TermsAuthTokenPayload;
import com.shinhan.corebank.signup.domain.model.UserIdCheckTokenPayload;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

// 초기 토큰 4개 → 임시 토큰 전환과, 추가 증빙을 선택적으로 소비하는 임시 토큰 회전을 검증한다.
class SignupTokenTransitionPersistenceAdapterTest extends IntegrationTestSupport {

    private static final Duration TTL = Duration.ofMinutes(30);

    @Autowired
    SignupTokenTransitionPersistenceAdapter adapter;

    @Autowired
    TermsAuthTokenPersistenceAdapter termsAuthTokenAdapter;

    @Autowired
    AccountAuthTokenPersistenceAdapter accountAuthTokenAdapter;

    @Autowired
    UserIdCheckTokenPersistenceAdapter userIdCheckTokenAdapter;

    @Autowired
    EmailVerificationTokenPersistenceAdapter emailVerificationTokenAdapter;

    @Autowired
    TempSignupTokenPersistenceAdapter tempSignupTokenAdapter;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private final List<String> tokens = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        tokens.forEach(token ->
                jdbcTemplate.update("DELETE FROM auth_token WHERE token_hash = ?", AuthTokenTestRows.hashOf(token)));
    }

    @Test
    @DisplayName("초기 토큰 4개를 소비하고 임시 회원가입 토큰을 발급한다")
    void replaceInitialTokensWithTemp() {
        String terms = newToken();
        String account = newToken();
        String userId = newToken();
        String email = newToken();
        String temp = newToken();
        termsAuthTokenAdapter.save(terms, new TermsAuthTokenPayload(List.of(), Instant.now()), TTL);
        accountAuthTokenAdapter.save(account, new AccountAuthTokenPayload("EBC-1", "EBA-1", Instant.now()), TTL);
        userIdCheckTokenAdapter.save(userId, new UserIdCheckTokenPayload("tester01", LocalDateTime.now()), TTL);
        emailVerificationTokenAdapter.save(email, emailPayload(), TTL);
        TempSignupTokenPayload payload = tempPayload();

        assertThat(adapter.replaceInitialTokensWithTemp(terms, account, userId, email, temp, payload, TTL))
                .isTrue();

        assertThat(termsAuthTokenAdapter.find(terms)).isEmpty();
        assertThat(accountAuthTokenAdapter.find(account)).isEmpty();
        assertThat(userIdCheckTokenAdapter.find(userId)).isEmpty();
        assertThat(emailVerificationTokenAdapter.find(email)).isEmpty();
        assertThat(tempSignupTokenAdapter.find(temp)).contains(payload);
    }

    @Test
    @DisplayName("추가 증빙 없이 임시 토큰만 회전하고, 주어진 증빙은 함께 소비한다")
    void rotateTempToken_withOptionalProofs() {
        String current = newToken();
        String email = newToken();
        String next = newToken();
        tempSignupTokenAdapter.save(current, tempPayload(), TTL);
        emailVerificationTokenAdapter.save(email, emailPayload(), TTL);

        assertThat(adapter.rotateTempToken(current, null, email, next, tempPayload(), TTL))
                .isTrue();

        assertThat(tempSignupTokenAdapter.find(current)).isEmpty();
        assertThat(emailVerificationTokenAdapter.find(email)).isEmpty();
        assertThat(tempSignupTokenAdapter.find(next)).isPresent();
    }

    @Test
    @DisplayName("현재 임시 토큰이 없으면 회전하지 않고 증빙 토큰도 남긴다")
    void rotateTempToken_withoutCurrentTemp_keepsProofs() {
        String email = newToken();
        emailVerificationTokenAdapter.save(email, emailPayload(), TTL);

        assertThat(adapter.rotateTempToken(newToken(), "", email, newToken(), tempPayload(), TTL))
                .isFalse();

        assertThat(emailVerificationTokenAdapter.find(email)).isPresent();
    }

    private EmailVerificationTokenPayload emailPayload() {
        return new EmailVerificationTokenPayload(
                "tester@example.com", EmailVerificationPurpose.SIGN_UP, LocalDateTime.now());
    }

    private TempSignupTokenPayload tempPayload() {
        return new TempSignupTokenPayload(
                List.of(new AgreedTerm("T1", "1.0")),
                "EBC-1",
                "EBA-1",
                "tester01",
                "$2a$10$hash",
                "tester@example.com",
                "01012345678",
                Instant.parse("2026-10-09T00:00:00Z"));
    }

    private String newToken() {
        String token = "SIGNUP_TEST_" + UUID.randomUUID();
        tokens.add(token);
        return token;
    }
}
