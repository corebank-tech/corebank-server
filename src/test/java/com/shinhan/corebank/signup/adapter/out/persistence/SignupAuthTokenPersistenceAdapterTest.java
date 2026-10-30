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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

// 회원가입 토큰 5종이 각자의 용도로 저장되고, TTL대로 만료 시각이 잡히며, 한 번만 소비되는지 검증한다.
class SignupAuthTokenPersistenceAdapterTest extends IntegrationTestSupport {

    private static final Duration TTL = Duration.ofMinutes(10);

    @Autowired
    TermsAuthTokenPersistenceAdapter termsAuthTokenAdapter;

    @Autowired
    UserIdCheckTokenPersistenceAdapter userIdCheckTokenAdapter;

    @Autowired
    EmailVerificationTokenPersistenceAdapter emailVerificationTokenAdapter;

    @Autowired
    AccountAuthTokenPersistenceAdapter accountAuthTokenAdapter;

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
    @DisplayName("약관 동의 토큰은 TERMS_AUTH로 저장되고 한 번만 소비된다")
    void termsAuthToken() {
        String token = newToken();
        TermsAuthTokenPayload payload = new TermsAuthTokenPayload(
                List.of(new AgreedTerm("T1", "1.0")), Instant.now().truncatedTo(ChronoUnit.MILLIS));

        termsAuthTokenAdapter.save(token, payload, TTL);

        assertStored(token, "TERMS_AUTH");
        assertThat(termsAuthTokenAdapter.find(token)).contains(payload);
        assertThat(termsAuthTokenAdapter.consume(token)).contains(payload);
        assertThat(termsAuthTokenAdapter.consume(token)).isEmpty();
    }

    @Test
    @DisplayName("아이디 확인 토큰은 USER_ID_CHECK로 저장되고 한 번만 소비된다")
    void userIdCheckToken() {
        String token = newToken();
        UserIdCheckTokenPayload payload =
                new UserIdCheckTokenPayload("tester01", LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));

        userIdCheckTokenAdapter.save(token, payload, TTL);

        assertStored(token, "USER_ID_CHECK");
        assertThat(userIdCheckTokenAdapter.consume(token)).contains(payload);
        assertThat(userIdCheckTokenAdapter.consume(token)).isEmpty();
    }

    @Test
    @DisplayName("이메일 인증 토큰은 EMAIL_VERIFICATION으로 저장되고 한 번만 소비된다")
    void emailVerificationToken() {
        String token = newToken();
        EmailVerificationTokenPayload payload = new EmailVerificationTokenPayload(
                "tester@example.com",
                EmailVerificationPurpose.SIGN_UP,
                LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));

        emailVerificationTokenAdapter.save(token, payload, TTL);

        assertStored(token, "EMAIL_VERIFICATION");
        assertThat(emailVerificationTokenAdapter.consume(token)).contains(payload);
        assertThat(emailVerificationTokenAdapter.consume(token)).isEmpty();
    }

    @Test
    @DisplayName("계좌 인증 토큰은 ACCOUNT_AUTH로 저장되고, 같은 원문이라도 다른 토큰 종류로는 찾지 못한다")
    void accountAuthToken_isolatedByPurpose() {
        String token = newToken();
        AccountAuthTokenPayload payload =
                new AccountAuthTokenPayload("EBC-1", "EBA-1", Instant.now().truncatedTo(ChronoUnit.MILLIS));

        accountAuthTokenAdapter.save(token, payload, TTL);

        assertStored(token, "ACCOUNT_AUTH");
        assertThat(termsAuthTokenAdapter.find(token)).isEmpty();
        assertThat(accountAuthTokenAdapter.consume(token)).contains(payload);
    }

    @Test
    @DisplayName("임시 회원가입 토큰은 TEMP_SIGNUP으로 저장되고, 빈 토큰은 찾지 않는다")
    void tempSignupToken() {
        String token = newToken();
        TempSignupTokenPayload payload = new TempSignupTokenPayload(
                List.of(new AgreedTerm("T1", "1.0")),
                "EBC-1",
                "EBA-1",
                "tester01",
                "$2a$10$hash",
                "tester@example.com",
                "01012345678",
                Instant.now().truncatedTo(ChronoUnit.MILLIS));

        tempSignupTokenAdapter.save(token, payload, TTL);

        assertStored(token, "TEMP_SIGNUP");
        assertThat(tempSignupTokenAdapter.find(" ")).isEmpty();
        assertThat(tempSignupTokenAdapter.find(token)).contains(payload);
        assertThat(tempSignupTokenAdapter.consume(token)).contains(payload);
        assertThat(tempSignupTokenAdapter.find(token)).isEmpty();
    }

    private void assertStored(String token, String purpose) {
        Long ttlSeconds = jdbcTemplate.queryForObject(
                """
                SELECT TIMESTAMPDIFF(SECOND, created_at, expires_at) FROM auth_token
                 WHERE token_hash = ? AND purpose = ? AND customer_id IS NULL
                """,
                Long.class,
                AuthTokenTestRows.hashOf(token),
                purpose);
        assertThat(ttlSeconds).isEqualTo(TTL.toSeconds());
    }

    private String newToken() {
        String token = "SIGNUP_TEST_" + UUID.randomUUID();
        tokens.add(token);
        return token;
    }
}
