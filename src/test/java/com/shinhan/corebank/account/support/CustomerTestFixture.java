package com.shinhan.corebank.account.support;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CustomerTestFixture {

    private static final String PASSWORD_HASH =
            "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq";

    private final JdbcTemplate jdbcTemplate;

    public Long createCustomer() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8);

        String userId = "accttest" + suffix;
        String email = "accttest" + suffix + "@example.com";

        LocalDateTime now = LocalDateTime.of(
                2026, 8, 10, 10, 0
        );

        jdbcTemplate.update(
                """
                INSERT INTO customer (
                    user_id,
                    password_hash,
                    user_name,
                    birth_date,
                    email,
                    phone_number,
                    login_failure_count,
                    account_locked,
                    last_login_at,
                    last_login_ip,
                    previous_login_at,
                    password_changed_at,
                    joined_at,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                userId,
                PASSWORD_HASH,
                "테스트",
                LocalDate.of(1990, 1, 1),
                email,
                "01012345678",
                0,
                false,
                null,
                null,
                null,
                now,
                now,
                now,
                now
        );

        return jdbcTemplate.queryForObject(
                """
                SELECT customer_id
                FROM customer
                WHERE user_id = ?
                """,
                Long.class,
                userId
        );
    }

    public void deleteCustomer(Long customerId) {
        jdbcTemplate.update(
                """
                DELETE FROM customer
                WHERE customer_id = ?
                """,
                customerId
        );
    }
}