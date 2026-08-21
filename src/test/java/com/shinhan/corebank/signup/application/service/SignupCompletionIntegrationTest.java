package com.shinhan.corebank.signup.application.service;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.common.idempotency.IdempotentRequestExecutor;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.signup.application.port.in.CompleteSignupCommand;
import com.shinhan.corebank.signup.adapter.out.redis.TempSignupTokenRedisAdapter;
import com.shinhan.corebank.signup.adapter.in.web.dto.CompleteSignupResponse;
import com.shinhan.corebank.signup.domain.model.AgreedTerm;
import com.shinhan.corebank.signup.domain.model.TempSignupTokenPayload;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import tools.jackson.core.type.TypeReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 회원가입 완료 시 고객·약관·전체 Mock 계좌가 함께 커밋되는지 검증한다.
@Transactional
class SignupCompletionIntegrationTest extends IntegrationTestSupport {

    private static final String PASSWORD_HASH =
            "$2y$10$1NOtaTsHuD0rdffA3ReFKO5S0J4bHlVES6okQMYubUd0OuVFfMZXa";

    @Autowired SignupCompletionService completionService;
    @Autowired TempSignupTokenRedisAdapter tempTokenAdapter;
    @Autowired IdempotentRequestExecutor idempotentRequestExecutor;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void registersCustomerAgreementsAndAllExistingBankAccounts() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String userId = "user" + suffix;
        String email = "user" + suffix + "@example.com";
        String token = "TEMP_SIGNUP_" + UUID.randomUUID();
        List<AgreedTerm> agreedTerms = signupTerms();
        tempTokenAdapter.save(
                token,
                new TempSignupTokenPayload(
                        agreedTerms,
                        "BANK_CUSTOMER_001",
                        "BANK_ACCOUNT_001",
                        userId,
                        PASSWORD_HASH,
                        email,
                        "01012345678",
                        Instant.now()
                ),
                Duration.ofMinutes(30)
        );

        var result = completionService.complete(
                new CompleteSignupCommand(token)
        );

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(count(
                "select count(*) from customer where customer_id = ?",
                result.customerId()
        )).isEqualTo(1);
        assertThat(count(
                "select count(*) from customer_terms_agreement where customer_id = ?",
                result.customerId()
        )).isEqualTo(agreedTerms.size());
        assertThat(count(
                "select count(*) from account where customer_id = ?",
                result.customerId()
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForList(
                "select balance from account where customer_id = ? order by account_number",
                Long.class,
                result.customerId()
        )).containsExactly(1_000_000L, 500_000L);
        assertThat(tempTokenAdapter.find(token)).isEmpty();
    }

    @Test
    void replaysCompletedSignupAndLinksIdempotencyKeyToCustomer() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String userId = "idem" + suffix;
        String token = "TEMP_SIGNUP_" + UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        tempTokenAdapter.save(
                token,
                payload(userId, token),
                Duration.ofMinutes(30)
        );
        AtomicInteger executions = new AtomicInteger();

        var first = executeSignup(key, token, executions);
        var replay = executeSignup(key, token, executions);

        assertThat(executions).hasValue(1);
        assertThat(replay.getBody().code()).isEqualTo(first.getBody().code());
        assertThat(replay.getBody().message())
                .isEqualTo(first.getBody().message());
        assertThat(replay.getBody().data().customerId())
                .isEqualTo(first.getBody().data().customerId());
        assertThat(replay.getBody().data().userId())
                .isEqualTo(first.getBody().data().userId());
        assertThat(replay.getBody().data().joinedAt().toInstant())
                .isEqualTo(first.getBody().data().joinedAt().toInstant());
        assertThat(jdbcTemplate.queryForObject(
                "select customer_id from idempotency_key where idempotency_key = ?",
                Long.class,
                key
        )).isEqualTo(first.getBody().data().customerId());

        assertThatThrownBy(() -> executeSignup(
                key,
                "TEMP_SIGNUP_DIFFERENT",
                executions
        )).isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode()
                ).isEqualTo(
                        CommonErrorCode.IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST
                ));
    }

    @Test
    void flywayAllowsNullCustomerOnlyForAnonymousIdempotencyReservation() {
        String nullable = jdbcTemplate.queryForObject(
                """
                select is_nullable
                  from information_schema.columns
                 where table_schema = database()
                   and table_name = 'idempotency_key'
                   and column_name = 'customer_id'
                """,
                String.class
        );

        assertThat(nullable).isEqualTo("YES");
    }

    private org.springframework.http.ResponseEntity<ApiResponse<CompleteSignupResponse>>
    executeSignup(String key, String token, AtomicInteger executions) {
        return idempotentRequestExecutor.executeAnonymous(
                key,
                "POST /auth/signup/complete",
                Map.of("tempSignupToken", token),
                new TypeReference<>() {
                },
                CompleteSignupResponse::customerId,
                () -> {
                    executions.incrementAndGet();
                    var result = completionService.complete(
                            new CompleteSignupCommand(token)
                    );
                    return ApiResponse.success(
                            CompleteSignupResponse.from(result),
                            "회원가입이 완료되었습니다."
                    );
                }
        );
    }

    private TempSignupTokenPayload payload(String userId, String token) {
        return new TempSignupTokenPayload(
                signupTerms(),
                "BANK_CUSTOMER_001",
                "BANK_ACCOUNT_001",
                userId,
                PASSWORD_HASH,
                userId + "@example.com",
                "01012345678",
                Instant.now()
        );
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

    private int count(String sql, Object... args) {
        Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                args
        );
        return count == null ? 0 : count;
    }
}
