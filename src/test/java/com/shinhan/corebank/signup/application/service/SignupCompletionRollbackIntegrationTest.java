package com.shinhan.corebank.signup.application.service;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.api.ExistingAccountRegistration;
import com.shinhan.corebank.signup.application.port.in.CompleteSignupCommand;
import com.shinhan.corebank.signup.adapter.out.redis.TempSignupTokenRedisAdapter;
import com.shinhan.corebank.signup.domain.model.AgreedTerm;
import com.shinhan.corebank.signup.domain.model.TempSignupTokenPayload;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;

// 계좌 등록 실패 시 고객·약관이 롤백되고 tempSignupToken이 복구되는지 검증한다.
class SignupCompletionRollbackIntegrationTest extends IntegrationTestSupport {

    private static final String PASSWORD_HASH =
            "$2y$10$1NOtaTsHuD0rdffA3ReFKO5S0J4bHlVES6okQMYubUd0OuVFfMZXa";

    @Autowired SignupCompletionService completionService;
    @Autowired TempSignupTokenRedisAdapter tempTokenAdapter;
    @Autowired JdbcTemplate jdbcTemplate;
    @MockitoBean ExistingAccountRegistration accountRegistration;

    @Test
    void rollsBackDatabaseAndRestoresTokenWhenAccountImportFails() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String userId = "rollback" + suffix;
        String token = "TEMP_SIGNUP_" + UUID.randomUUID();
        TempSignupTokenPayload payload = new TempSignupTokenPayload(
                signupTerms(),
                "BANK_CUSTOMER_001",
                "BANK_ACCOUNT_001",
                userId,
                PASSWORD_HASH,
                userId + "@example.com",
                "01012345678",
                Instant.now()
        );
        tempTokenAdapter.save(token, payload, Duration.ofMinutes(30));
        willThrow(new IllegalStateException("account import failure"))
                .given(accountRegistration)
                .registerAll(any());

        assertThatThrownBy(() -> completionService.complete(
                new CompleteSignupCommand(token)
        )).isInstanceOf(IllegalStateException.class);

        Integer customerCount = jdbcTemplate.queryForObject(
                "select count(*) from customer where user_id = ?",
                Integer.class,
                userId
        );
        assertThat(customerCount).isZero();
        assertThat(tempTokenAdapter.find(token)).contains(payload);
    }

    @Test
    void rollsBackCustomerWhenAgreedTermsVersionIsInvalid() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String userId = "terms" + suffix;
        String token = "TEMP_SIGNUP_" + UUID.randomUUID();
        TempSignupTokenPayload payload = new TempSignupTokenPayload(
                List.of(new AgreedTerm("999999999", "invalid")),
                "BANK_CUSTOMER_001",
                "BANK_ACCOUNT_001",
                userId,
                PASSWORD_HASH,
                userId + "@example.com",
                "01012345678",
                Instant.now()
        );
        tempTokenAdapter.save(token, payload, Duration.ofMinutes(30));

        assertThatThrownBy(() -> completionService.complete(
                new CompleteSignupCommand(token)
        )).isInstanceOf(IllegalStateException.class);

        Integer customerCount = jdbcTemplate.queryForObject(
                "select count(*) from customer where user_id = ?",
                Integer.class,
                userId
        );
        assertThat(customerCount).isZero();
        assertThat(tempTokenAdapter.find(token)).contains(payload);
    }

    private List<AgreedTerm> signupTerms() {
        return jdbcTemplate.query(
                "select terms_id, version from terms where terms_type = 'SIGNUP' order by terms_id",
                (resultSet, rowNumber) -> new AgreedTerm(
                        resultSet.getString("terms_id"),
                        resultSet.getString("version")
                )
        );
    }
}
