package com.shinhan.corebank.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.auth.application.port.in.IssuePasswordResetCommand;
import com.shinhan.corebank.auth.application.port.in.IssuePasswordResetResult;
import com.shinhan.corebank.auth.application.port.in.IssuePasswordResetUseCase;
import com.shinhan.corebank.auth.application.port.in.ResetPasswordCommand;
import com.shinhan.corebank.auth.application.port.in.ResetPasswordUseCase;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.customer.application.port.out.CustomerPersistencePort;
import com.shinhan.corebank.customer.domain.model.Customer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

// 동일 고객의 동시 재발급이 고객 행 잠금으로 직렬화되는지 실제 MySQL에서 검증한다.
@DisplayName("비밀번호 재설정 인증번호 동시 재발급 통합 테스트")
class PasswordResetIssueConcurrencyIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private IssuePasswordResetUseCase issueUseCase;

    @Autowired
    private ResetPasswordUseCase resetUseCase;

    @Autowired
    private CustomerPersistencePort customerPersistencePort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    private ExecutorService executor;
    private Long customerId;
    private String userId;
    private String email;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        userId = "prc" + suffix;
        email = "prc-" + suffix + "@example.com";
        Customer customer = Customer.register(
                userId,
                null,
                "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq",
                "홍길동",
                LocalDate.of(1990, 1, 1),
                email,
                "01012345678",
                LocalDateTime.of(2026, 9, 21, 9, 0));
        customerId = customerPersistencePort.save(customer).getCustomerId();
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        jdbcTemplate.update("DELETE FROM verification_request WHERE customer_id = ?", customerId);
        jdbcTemplate.update("DELETE FROM customer WHERE customer_id = ?", customerId);
    }

    @Test
    @DisplayName("동일 고객이 동시에 재발급하면 마지막 요청만 활성 상태로 남는다")
    void concurrentIssueLeavesOnlyLatestRequestActive() throws Exception {
        CountDownLatch anyFinished = new CountDownLatch(1);
        List<IssuePasswordResetResult> results;

        try (Connection blocker = dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            try (PreparedStatement lockCustomer =
                    blocker.prepareStatement("SELECT customer_id FROM customer WHERE customer_id = ? FOR UPDATE")) {
                lockCustomer.setLong(1, customerId);
                lockCustomer.executeQuery();
            }

            Future<IssuePasswordResetResult> first = submitIssue(anyFinished);
            Future<IssuePasswordResetResult> second = submitIssue(anyFinished);

            assertThat(anyFinished.await(1, TimeUnit.SECONDS)).isFalse();
            blocker.commit();

            results = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        }

        Long activeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM verification_request "
                        + "WHERE customer_id = ? AND purpose = 'PASSWORD_RESET' AND used = false",
                Long.class,
                customerId);
        assertThat(activeCount).isEqualTo(1L);
        Long invalidatedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM verification_request "
                        + "WHERE customer_id = ? AND purpose = 'PASSWORD_RESET' AND used = true",
                Long.class,
                customerId);
        assertThat(invalidatedCount).isEqualTo(1L);

        String activeRequestId = jdbcTemplate.queryForObject(
                "SELECT verification_request_id FROM verification_request "
                        + "WHERE customer_id = ? AND purpose = 'PASSWORD_RESET' AND used = false",
                String.class,
                customerId);
        IssuePasswordResetResult active = results.stream()
                .filter(result -> result.passwordResetRequestId().equals(activeRequestId))
                .findFirst()
                .orElseThrow();
        IssuePasswordResetResult invalidated = results.stream()
                .filter(result -> !result.passwordResetRequestId().equals(activeRequestId))
                .findFirst()
                .orElseThrow();

        assertThatThrownBy(() -> resetUseCase.reset(reset(invalidated)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(
                                exception.getErrorCode().getCode())
                        .isEqualTo("ATH0202"));
        assertThat(resetUseCase.reset(reset(active)).customerId()).isEqualTo(customerId);
    }

    private Future<IssuePasswordResetResult> submitIssue(CountDownLatch anyFinished) {
        return executor.submit(() -> {
            try {
                return issueUseCase.issue(new IssuePasswordResetCommand(userId, "홍길동", email));
            } finally {
                anyFinished.countDown();
            }
        });
    }

    private ResetPasswordCommand reset(IssuePasswordResetResult result) {
        return new ResetPasswordCommand(
                result.passwordResetRequestId(), result.verificationCode(), "Qx7!mN2@pL", "Qx7!mN2@pL");
    }
}
