package com.shinhan.corebank.subscription.adapter.out.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class SubscriptionTestFixtures {

    private SubscriptionTestFixtures() {
    }

    public static Long insertCustomer(JdbcTemplate jdbcTemplate, String userId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO customer (user_id, password_hash, user_name, birth_date, email, phone_number, joined_at, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            LocalDateTime now = LocalDateTime.now();
            ps.setString(1, userId);
            ps.setString(2, "$2a$10$abcdefghijklmnopqrstuvABCDEFGHIJKLMNOPQRSTUVWXYZabcde");
            ps.setString(3, "홍길동");
            ps.setObject(4, LocalDate.of(1990, 1, 1));
            ps.setString(5, userId + "@example.com");
            ps.setString(6, "01012345678");
            ps.setObject(7, now);
            ps.setObject(8, now);
            ps.setObject(9, now);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public static Long insertAccount(JdbcTemplate jdbcTemplate, String accountNumber, Long customerId, Long productId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO account (account_number, customer_id, product_id, account_type, status, password_hash, opened_date, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            LocalDateTime now = LocalDateTime.now();
            ps.setString(1, accountNumber);
            ps.setLong(2, customerId);
            if (productId == null) {
                ps.setNull(3, java.sql.Types.BIGINT);
            } else {
                ps.setLong(3, productId);
            }
            ps.setString(4, productId == null ? "DEMAND_DEPOSIT" : "TIME_DEPOSIT");
            ps.setString(5, "ACTIVE");
            ps.setString(6, "$2a$10$abcdefghijklmnopqrstuvABCDEFGHIJKLMNOPQRSTUVWXYZabcde");
            ps.setObject(7, now);
            ps.setObject(8, now);
            ps.setObject(9, now);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }
}
