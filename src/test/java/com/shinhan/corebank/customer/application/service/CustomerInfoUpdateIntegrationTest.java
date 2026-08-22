package com.shinhan.corebank.customer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.customer.application.port.in.UpdateCustomerInfoCommand;
import com.shinhan.corebank.customer.application.port.in.UpdateCustomerInfoResult;
import com.shinhan.corebank.customer.application.port.in.UpdateCustomerInfoUseCase;
import com.shinhan.corebank.signup.application.port.out.EmailVerificationTokenPort;
import com.shinhan.corebank.signup.domain.exception.SignupErrorCode;
import com.shinhan.corebank.signup.domain.model.EmailVerificationPurpose;
import com.shinhan.corebank.signup.domain.model.EmailVerificationTokenPayload;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

@DisplayName("고객정보 변경 MySQL·Redis 통합 테스트")
class CustomerInfoUpdateIntegrationTest extends IntegrationTestSupport {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    @Autowired
    UpdateCustomerInfoUseCase updateCustomerInfoUseCase;

    @Autowired
    EmailVerificationTokenPort emailVerificationTokenPort;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private final List<Long> customerIds = new ArrayList<>();
    private final List<String> emailTokens = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (String token : emailTokens) {
            emailVerificationTokenPort.consume(token);
        }
        for (Long customerId : customerIds) {
            jdbcTemplate.update(
                    "DELETE FROM customer WHERE customer_id = ?",
                    customerId
            );
        }
    }

    @Test
    @DisplayName("인증된 이메일과 휴대폰 번호를 저장하고 Redis 토큰을 소비한다")
    void updatesContactInfoAndConsumesEmailToken() {
        long sequence = SEQUENCE.incrementAndGet();
        Long customerId = insertCustomer(sequence);
        String token = "EMAIL_VERIFICATION_update_" + sequence;
        String newEmail = "updated" + sequence + "@corebank.com";
        saveEmailToken(token, newEmail, EmailVerificationPurpose.EMAIL_CHANGE);
        LocalDateTime originalUpdatedAt = selectUpdatedAt(customerId);

        UpdateCustomerInfoResult result = updateCustomerInfoUseCase.update(
                new UpdateCustomerInfoCommand(
                        customerId,
                        "01087654321",
                        newEmail,
                        token
                )
        );

        assertThat(result.customerId()).isEqualTo(customerId);
        assertThat(result.phoneNumber()).isEqualTo("010****4321");
        assertThat(result.email()).startsWith("upda");
        assertThat(selectPhoneNumber(customerId)).isEqualTo("01087654321");
        assertThat(selectEmail(customerId)).isEqualTo(newEmail);
        LocalDateTime persistedUpdatedAt = selectUpdatedAt(customerId);
        assertThat(persistedUpdatedAt).isAfter(originalUpdatedAt);
        assertThat(result.updatedAt().toLocalDateTime()
                .truncatedTo(ChronoUnit.MICROS))
                .isEqualTo(persistedUpdatedAt
                        .truncatedTo(ChronoUnit.MICROS));
        assertThat(emailVerificationTokenPort.find(token)).isEmpty();
    }

    @Test
    @DisplayName("이메일 인증 토큰이 유효하지 않으면 DB 변경을 롤백한다")
    void rollsBackContactInfoWhenEmailTokenIsInvalid() {
        long sequence = SEQUENCE.incrementAndGet();
        Long customerId = insertCustomer(sequence);
        String originalEmail = "current" + sequence + "@corebank.com";
        String token = "EMAIL_VERIFICATION_invalid_" + sequence;
        String requestedEmail = "changed" + sequence + "@corebank.com";
        saveEmailToken(
                token,
                requestedEmail,
                EmailVerificationPurpose.SIGN_UP
        );

        BusinessException exception = catchThrowableOfType(
                () -> updateCustomerInfoUseCase.update(
                        new UpdateCustomerInfoCommand(
                                customerId,
                                "01087654321",
                                requestedEmail,
                                token
                        )
                ),
                BusinessException.class
        );

        assertThat(exception.getErrorCode()).isEqualTo(
                SignupErrorCode.INVALID_EMAIL_VERIFICATION_TOKEN
        );
        assertThat(selectPhoneNumber(customerId)).isEqualTo("01012345678");
        assertThat(selectEmail(customerId)).isEqualTo(originalEmail);
        assertThat(emailVerificationTokenPort.find(token)).isPresent();
    }

    // 실제 Redis에 이메일 인증 완료 토큰을 저장한다.
    private void saveEmailToken(
            String token,
            String email,
            EmailVerificationPurpose purpose
    ) {
        emailTokens.add(token);
        emailVerificationTokenPort.save(
                token,
                new EmailVerificationTokenPayload(
                        email,
                        purpose,
                        LocalDateTime.now()
                ),
                Duration.ofMinutes(30)
        );
    }

    // 통합 테스트용 고객을 실제 MySQL에 저장하고 생성된 PK를 반환한다.
    private Long insertCustomer(long sequence) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO customer "
                            + "(user_id, password_hash, user_name, birth_date, "
                            + "email, phone_number, joined_at, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            LocalDateTime now = LocalDateTime.of(2026, 8, 21, 16, 0);
            statement.setString(1, "update-user-" + sequence);
            statement.setString(2, "x");
            statement.setString(3, "홍길동");
            statement.setString(4, "1995-03-10");
            statement.setString(5, "current" + sequence + "@corebank.com");
            statement.setString(6, "01012345678");
            statement.setObject(7, now);
            statement.setObject(8, now);
            statement.setObject(9, now);
            return statement;
        }, keyHolder);

        Long customerId = keyHolder.getKey().longValue();
        customerIds.add(customerId);
        return customerId;
    }

    // 저장된 고객의 휴대폰 번호를 MySQL에서 직접 조회한다.
    private String selectPhoneNumber(Long customerId) {
        return jdbcTemplate.queryForObject(
                "SELECT phone_number FROM customer WHERE customer_id = ?",
                String.class,
                customerId
        );
    }

    // 저장된 고객의 이메일을 MySQL에서 직접 조회한다.
    private String selectEmail(Long customerId) {
        return jdbcTemplate.queryForObject(
                "SELECT email FROM customer WHERE customer_id = ?",
                String.class,
                customerId
        );
    }

    // JPA Auditing이 실제 MySQL에 기록한 고객정보 변경일시를 조회한다.
    private LocalDateTime selectUpdatedAt(Long customerId) {
        return jdbcTemplate.queryForObject(
                "SELECT updated_at FROM customer WHERE customer_id = ?",
                LocalDateTime.class,
                customerId
        );
    }
}
