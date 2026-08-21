package com.shinhan.corebank.otp.application.service;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.support.CustomerTestFixture;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.otp.api.OtpTransactionType;
import com.shinhan.corebank.otp.application.port.in.IssueOtpCommand;
import com.shinhan.corebank.otp.application.port.in.IssueOtpResult;
import com.shinhan.corebank.otp.application.port.in.IssueOtpUseCase;
import com.shinhan.corebank.otp.application.port.in.VerifyOtpCommand;
import com.shinhan.corebank.otp.application.port.in.VerifyOtpResult;
import com.shinhan.corebank.otp.application.port.in.VerifyOtpUseCase;
import com.shinhan.corebank.otp.domain.exception.OtpVerificationFailedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// 동시 OTP 발급·성공 검증·오답 검증에서 활성 요청과 횟수의 원자성을 검증한다.
class OtpConcurrencyIntegrationTest extends IntegrationTestSupport {

    private static final String REDIS_PREFIX = "otp:auth:";

    @Autowired CustomerTestFixture customerFixture;
    @Autowired IssueOtpUseCase issueOtpUseCase;
    @Autowired VerifyOtpUseCase verifyOtpUseCase;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired StringRedisTemplate redisTemplate;

    private Long customerId;
    private final List<String> authTokens = new CopyOnWriteArrayList<>();

    @AfterEach
    void cleanUp() {
        authTokens.forEach(token -> redisTemplate.delete(REDIS_PREFIX + token));
        if (customerId != null) {
            jdbcTemplate.update(
                    "DELETE FROM verification_request WHERE customer_id = ?",
                    customerId
            );
            customerFixture.deleteCustomer(customerId);
        }
    }

    @Test
    @DisplayName("동시에 OTP를 발급해도 두 요청이 순차 성공하고 활성 OTP는 하나만 남는다")
    void concurrentIssueLeavesOneActiveRequest() throws Exception {
        customerId = customerFixture.createCustomer();
        IssueOtpCommand command = issueCommand(customerId);

        List<Invocation<IssueOtpResult>> invocations = invokeConcurrently(
                2,
                () -> issueOtpUseCase.issue(command)
        );

        assertThat(invocations).allMatch(Invocation::succeeded);
        assertThat(activeRequestCount(customerId)).isOne();
    }

    @Test
    @DisplayName("동일 OTP를 동시에 검증해도 한 요청만 성공하고 DB 요청은 한 번만 사용된다")
    void concurrentCorrectVerificationSucceedsOnce() throws Exception {
        customerId = customerFixture.createCustomer();
        IssueOtpResult issued = issueOtpUseCase.issue(issueCommand(customerId));
        VerifyOtpCommand command = new VerifyOtpCommand(
                customerId,
                issued.otpRequestId(),
                issued.otpCode()
        );

        List<Invocation<VerifyOtpResult>> invocations = invokeConcurrently(
                2,
                () -> verifyOtpUseCase.verify(command)
        );
        invocations.stream()
                .filter(Invocation::succeeded)
                .map(Invocation::value)
                .map(VerifyOtpResult::otpAuthToken)
                .forEach(authTokens::add);

        assertThat(invocations.stream().filter(Invocation::succeeded).count()).isOne();
        assertThat(invocations.stream().filter(invocation -> !invocation.succeeded()).count())
                .isOne();
        assertThat(invocations.stream()
                .filter(invocation -> !invocation.succeeded())
                .map(Invocation::failure)
                .allMatch(failure -> failure instanceof BusinessException exception
                        && exception.getErrorCode().getCode().equals("OTP0201")))
                .isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT used FROM verification_request WHERE verification_request_id = ?",
                Boolean.class,
                issued.otpRequestId()
        )).isTrue();
    }

    @Test
    @DisplayName("다섯 개의 동시 오답도 횟수 유실 없이 error_count 5와 잠금으로 저장된다")
    void concurrentWrongVerificationKeepsAllFailures() throws Exception {
        customerId = customerFixture.createCustomer();
        IssueOtpResult issued = issueOtpUseCase.issue(issueCommand(customerId));
        String wrongCode = issued.otpCode().equals("000000") ? "999999" : "000000";
        VerifyOtpCommand command = new VerifyOtpCommand(
                customerId,
                issued.otpRequestId(),
                wrongCode
        );

        List<Invocation<VerifyOtpResult>> invocations = invokeConcurrently(
                5,
                () -> verifyOtpUseCase.verify(command)
        );

        assertThat(invocations).allMatch(invocation ->
                invocation.failure() instanceof OtpVerificationFailedException
        );
        Map<String, Object> state = jdbcTemplate.queryForMap(
                """
                SELECT error_count, locked
                FROM verification_request
                WHERE verification_request_id = ?
                """,
                issued.otpRequestId()
        );
        assertThat(((Number) state.get("error_count")).intValue()).isEqualTo(5);
        assertThat(state.get("locked")).isEqualTo(true);
    }

    private IssueOtpCommand issueCommand(Long currentCustomerId) {
        return new IssueOtpCommand(
                currentCustomerId,
                OtpTransactionType.IMMEDIATE_TRANSFER,
                Map.of(
                        "withdrawalAccountId", 101L,
                        "depositAccountNumber", "110660000103",
                        "amount", 100_000L
                )
        );
    }

    private int activeRequestCount(Long currentCustomerId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM verification_request
                WHERE customer_id = ?
                  AND purpose = 'OTP_TRANSACTION'
                  AND used = FALSE
                  AND locked = FALSE
                  AND expires_at > (
                      SELECT MAX(reference.created_at)
                      FROM verification_request reference
                      WHERE reference.customer_id = ?
                        AND reference.purpose = 'OTP_TRANSACTION'
                  )
                """,
                Integer.class,
                currentCustomerId,
                currentCustomerId
        );
    }

    private <T> List<Invocation<T>> invokeConcurrently(
            int count,
            Callable<T> action
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Invocation<T>>> futures = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        return Invocation.success(action.call());
                    } catch (Throwable failure) {
                        return Invocation.failure(failure);
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Invocation<T>> results = new ArrayList<>();
            for (Future<Invocation<T>> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private record Invocation<T>(T value, Throwable failure) {
        static <T> Invocation<T> success(T value) {
            return new Invocation<>(value, null);
        }

        static <T> Invocation<T> failure(Throwable failure) {
            return new Invocation<>(null, failure);
        }

        boolean succeeded() {
            return failure == null;
        }
    }
}
