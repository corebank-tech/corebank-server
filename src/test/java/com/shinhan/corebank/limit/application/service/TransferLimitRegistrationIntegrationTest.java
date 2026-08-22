package com.shinhan.corebank.limit.application.service;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.api.ExistingAccountRegistration;
import com.shinhan.corebank.limit.api.TransferLimitRegistration;
import com.shinhan.corebank.signup.adapter.out.redis.TempSignupTokenRedisAdapter;
import com.shinhan.corebank.signup.application.port.in.CompleteSignupCommand;
import com.shinhan.corebank.signup.application.service.SignupCompletionService;
import com.shinhan.corebank.signup.domain.model.AgreedTerm;
import com.shinhan.corebank.signup.domain.model.TempSignupTokenPayload;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

// 회원가입 트랜잭션이 이체한도 기본값까지 함께 만들고 함께 롤백하는지 검증한다(REQ-TRSF-029).
// 커밋 여부 자체가 검증 대상이라 클래스에 @Transactional 을 걸지 않는다.
class TransferLimitRegistrationIntegrationTest extends IntegrationTestSupport {

    private static final String PASSWORD_HASH =
            "$2y$10$1NOtaTsHuD0rdffA3ReFKO5S0J4bHlVES6okQMYubUd0OuVFfMZXa";
    private static final long DEFAULT_ONE_TIME_LIMIT = 1_000_000L;
    private static final long DEFAULT_DAILY_LIMIT = 5_000_000L;

    @Autowired SignupCompletionService completionService;
    @Autowired TempSignupTokenRedisAdapter tempTokenAdapter;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean ExistingAccountRegistration accountRegistration;
    @MockitoSpyBean TransferLimitRegistration transferLimitRegistration;

    @Test
    void assignsPolicyDefaultLimitWhenSignupCompletes() {
        String userId = "lmtok" + suffix();
        String token = saveTempToken(userId);

        var result = completionService.complete(new CompleteSignupCommand(token));

        assertThat(jdbcTemplate.queryForObject(
                "select one_time_limit from transfer_limit where customer_id = ?",
                Long.class, result.customerId()))
                .isEqualTo(DEFAULT_ONE_TIME_LIMIT);
        assertThat(jdbcTemplate.queryForObject(
                "select daily_limit from transfer_limit where customer_id = ?",
                Long.class, result.customerId()))
                .isEqualTo(DEFAULT_DAILY_LIMIT);
    }

    // created_at 은 한도 최초 부여 일시다. 가입과 한 트랜잭션이므로 가입 시각과 같은 날이어야 한다.
    @Test
    void stampsCreatedAtWhenSignupCompletes() {
        String userId = "lmtat" + suffix();
        String token = saveTempToken(userId);

        var result = completionService.complete(new CompleteSignupCommand(token));

        assertThat(jdbcTemplate.queryForObject(
                "select date(tl.created_at) = date(c.joined_at)"
                        + " from transfer_limit tl join customer c on c.customer_id = tl.customer_id"
                        + " where tl.customer_id = ?",
                Boolean.class, result.customerId()))
                .isTrue();
    }

    // 가입이 실패하면 한도도 남지 않는다. 리뷰어(PR #253)가 지적한 "둘 중 하나만 성공하는 경로"가 없다는 뜻이다.
    //
    // 고객 아이디로 조인해 세지 않고 캡처한 customerId 로 직접 센다. 조인으로 세면 한도를 아예
    // 만들지 않아도 0건이 나와 공허하게 참이 된다 - 실제로 registerDefault 호출을 지우고 돌렸을 때
    // 이 테스트만 통과했다. 캡처는 호출이 있었어야 값이 생기므로 그 구멍을 막는다.
    @Test
    void leavesNoLimitWhenSignupRollsBack() {
        String userId = "lmtrb" + suffix();
        String token = saveTempToken(userId);
        willThrow(new IllegalStateException("account import failure"))
                .given(accountRegistration).registerAll(any());

        assertThatThrownBy(() -> completionService.complete(new CompleteSignupCommand(token)))
                .isInstanceOf(IllegalStateException.class);

        ArgumentCaptor<Long> customerIdCaptor = ArgumentCaptor.forClass(Long.class);
        then(transferLimitRegistration).should().registerDefault(customerIdCaptor.capture());
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from transfer_limit where customer_id = ?",
                Integer.class, customerIdCaptor.getValue()))
                .isZero();
    }

    // 반대 방향. 한도 생성이 실패하면 고객도 남지 않는다 - 한도가 없는 고객이 생기지 않는다.
    @Test
    void rollsBackSignupWhenLimitRegistrationFails() {
        String userId = "lmtfa" + suffix();
        String token = saveTempToken(userId);
        willThrow(new IllegalStateException("limit registration failure"))
                .given(transferLimitRegistration).registerDefault(any());

        assertThatThrownBy(() -> completionService.complete(new CompleteSignupCommand(token)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from customer where user_id = ?", Integer.class, userId))
                .isZero();
    }

    private String saveTempToken(String userId) {
        String token = "TEMP_SIGNUP_" + UUID.randomUUID();
        tempTokenAdapter.save(token, new TempSignupTokenPayload(
                signupTerms(),
                "BANK_CUSTOMER_001",
                "BANK_ACCOUNT_001",
                userId,
                PASSWORD_HASH,
                userId + "@example.com",
                "01012345678",
                Instant.now()
        ), Duration.ofMinutes(30));
        return token;
    }

    private String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
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
