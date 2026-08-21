package com.shinhan.corebank.customer.application.service;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.auth.application.port.in.LoginCommand;
import com.shinhan.corebank.auth.application.port.in.LoginUseCase;
import com.shinhan.corebank.auth.domain.exception.AuthErrorCode;
import com.shinhan.corebank.auth.domain.exception.LoginFailedException;
import com.shinhan.corebank.auth.domain.model.LoginAttemptResult;
import com.shinhan.corebank.customer.api.CustomerAuthenticationFacade;
import com.shinhan.corebank.customer.api.LoginFailureState;
import com.shinhan.corebank.customer.api.RecordLoginFailureCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;

@DisplayName("로그인 실패 동시성 통합 테스트")
class CustomerLoginFailureConcurrencyIntegrationTest
        extends IntegrationTestSupport {

    private static final String USER_ID = "login-lock-user";
    private static final String EMAIL = "login-lock@example.com";
    private static final String PASSWORD_HASH =
            "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq";

    @Autowired
    private CustomerAuthenticationFacade customerAuthenticationFacade;

    @Autowired
    private LoginUseCase loginUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ExecutorService executor;
    private Long customerId;

    @BeforeEach
    void setUp() {
        deleteTestCustomer();
        customerId = insertCustomerWithThreeFailures();
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        boolean terminated = terminateExecutor();

        if (terminated) {
            deleteTestCustomer();
        }

        assertThat(terminated)
                .as("동시성 테스트 작업 스레드가 정상적으로 종료되어야 한다.")
                .isTrue();
    }

    @Test
    @DisplayName("실패 횟수 3회에서 두 요청이 동시에 실패하면 5회로 잠긴다")
    void incrementsEveryConcurrentLoginFailure() throws Exception {
        CountDownLatch startLatch = new CountDownLatch(1);

        Future<LoginFailureState> firstRequest =
                submitLoginFailure(startLatch);
        Future<LoginFailureState> secondRequest =
                submitLoginFailure(startLatch);

        // 두 요청이 같은 시점에 로그인 실패 처리를 시작
        startLatch.countDown();

        List<LoginFailureState> results = List.of(
                firstRequest.get(30, TimeUnit.SECONDS),
                secondRequest.get(30, TimeUnit.SECONDS)
        );

        assertThat(results)
                .extracting(
                        LoginFailureState::loginFailureCount,
                        LoginFailureState::accountLocked
                )
                .containsExactlyInAnyOrder(
                        tuple(4, false),
                        tuple(5, true)
                );

        PersistedCustomerState persistedState = findPersistedCustomerState();

        assertThat(persistedState.loginFailureCount()).isEqualTo(5);
        assertThat(persistedState.accountLocked()).isTrue();
        assertUnrelatedCustomerFieldsUnchanged(persistedState);
    }

    // 로그인 실패 예외는 고객 상태 트랜잭션을 롤백하지 않음
    @Test
    @DisplayName("ATH0101 예외가 발생해도 실패 횟수와 다른 고객 필드가 유지된다")
    void persistsFailureStateBeforeThrowingLoginException() {
        LoginFailedException exception = catchThrowableOfType(
                () -> loginUseCase.login(
                        new LoginCommand(
                                USER_ID,
                                "WrongPassword1!",
                                "192.168.0.10"
                        )
                ),
                LoginFailedException.class
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(AuthErrorCode.LOGIN_FAILED);
        assertThat(exception.getAttemptResult())
                .contains(new LoginAttemptResult(4, 1));

        PersistedCustomerState persistedState = findPersistedCustomerState();

        assertThat(persistedState.loginFailureCount()).isEqualTo(4);
        assertThat(persistedState.accountLocked()).isFalse();
        assertUnrelatedCustomerFieldsUnchanged(persistedState);
    }

    private Future<LoginFailureState> submitLoginFailure(
            CountDownLatch startLatch
    ) {
        return executor.submit(() -> {
            startLatch.await();

            return customerAuthenticationFacade.updateLoginFailureState(
                    new RecordLoginFailureCommand(customerId)
            );
        });
    }

    private Long insertCustomerWithThreeFailures() {
        jdbcTemplate.update("""
                INSERT INTO customer (
                    user_id,
                    password_hash,
                    user_name,
                    birth_date,
                    email,
                    phone_number,
                    login_failure_count,
                    account_locked,
                    joined_at,
                    created_at,
                    updated_at
                ) VALUES (
                    ?, ?, ?, ?, ?, ?, 3, FALSE,
                    NOW(6), NOW(6), NOW(6)
                )
                """,
                USER_ID,
                PASSWORD_HASH,
                "동시성테스트",
                "1990-01-01",
                EMAIL,
                "01012345678"
        );

        return jdbcTemplate.queryForObject(
                "SELECT customer_id FROM customer WHERE user_id = ?",
                Long.class,
                USER_ID
        );
    }

    private PersistedCustomerState findPersistedCustomerState() {
        return jdbcTemplate.queryForObject(
                """
                SELECT
                    login_failure_count,
                    account_locked,
                    password_hash,
                    email,
                    phone_number
                FROM customer
                WHERE customer_id = ?
                """,
                (resultSet, rowNumber) -> new PersistedCustomerState(
                        resultSet.getInt("login_failure_count"),
                        resultSet.getBoolean("account_locked"),
                        resultSet.getString("password_hash"),
                        resultSet.getString("email"),
                        resultSet.getString("phone_number")
                ),
                customerId
        );
    }

    private void assertUnrelatedCustomerFieldsUnchanged(
            PersistedCustomerState persistedState
    ) {
        assertThat(persistedState.passwordHash()).isEqualTo(PASSWORD_HASH);
        assertThat(persistedState.email()).isEqualTo(EMAIL);
        assertThat(persistedState.phoneNumber()).isEqualTo("01012345678");
    }

    private void deleteTestCustomer() {
        jdbcTemplate.update(
                """
                DELETE FROM audit_log
                WHERE customer_id IN (
                    SELECT customer_id
                    FROM customer
                    WHERE user_id = ?
                )
                """,
                USER_ID
        );
        jdbcTemplate.update(
                "DELETE FROM customer WHERE user_id = ?",
                USER_ID
        );
    }

    private boolean terminateExecutor() throws InterruptedException {
        if (executor == null) {
            return true;
        }

        executor.shutdownNow();

        return executor.awaitTermination(30, TimeUnit.SECONDS);
    }

    // 로그인 상태와 덮어쓰기 방지 대상 컬럼 조회 결과
    private record PersistedCustomerState(
            int loginFailureCount,
            boolean accountLocked,
            String passwordHash,
            String email,
            String phoneNumber
    ) {
    }
}
