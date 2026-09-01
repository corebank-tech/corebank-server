package com.shinhan.corebank.subscription.adapter.out.persistence;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.subscription.domain.MaturityHandling;
import com.shinhan.corebank.subscription.domain.ProductSubscription;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

public final class SubscriptionTestFixtures {

    /** customer.password_hash / account.password_hash가 CHAR(60)이라 정확히 60자여야 한다. */
    private static final String DUMMY_BCRYPT_HASH = "$2a$10$abcdefghijklmnopqrstuvABCDEFGHIJKLMNOPQRSTUVWXYZabcde";

    private SubscriptionTestFixtures() {}

    /**
     * 전 필드가 채워진 가입 엔티티(Object Mother). 시각은 DATETIME(6) 마이크로초 절삭과
     * 무관하게 round-trip 비교가 가능하도록 고정값을 쓴다.
     * paymentDay는 적립식 전용 컬럼이지만 DB 제약이 없어 Byte↔TINYINT 매핑 검증 목적으로 채운다.
     */
    public static ProductSubscriptionJpaEntity defaultSubscription(
            Long customerId, Long productId, Long withdrawalAccountId) {
        return defaultSubscription(customerId, productId, withdrawalAccountId, null);
    }

    public static ProductSubscriptionJpaEntity defaultSubscription(
            Long customerId, Long productId, Long withdrawalAccountId, Long accountId) {
        return ProductSubscriptionJpaEntity.builder()
                .customerId(customerId)
                .productId(productId)
                .accountId(accountId)
                .withdrawalAccountId(withdrawalAccountId)
                .subscriptionAmount(1_000_000L)
                .termMonths((short) 12)
                .paymentDay((byte) 15)
                .baseRate(new BigDecimal("2.50"))
                .preferentialRate(new BigDecimal("0.30"))
                .appliedRate(new BigDecimal("2.80"))
                .maturityHandling(MaturityHandling.TRANSFER)
                .expectedMaturityAmount(1_028_000L)
                .status(ProcessResultStatus.SUCCESS)
                .transactionNumber("20260801000000000001")
                .openedDate(LocalDate.of(2026, 8, 1))
                .maturityDate(LocalDate.of(2027, 8, 1))
                .subscribedAt(LocalDateTime.of(2026, 8, 1, 10, 30, 0))
                .build();
    }

    /** Mapper 왕복 테스트용 도메인 객체. {@link #defaultSubscription}과 같은 값 세트를 쓴다. */
    public static ProductSubscription defaultSubscriptionDomain() {
        return ProductSubscription.builder()
                .subscriptionId(1L)
                .customerId(2L)
                .productId(3L)
                .accountId(4L)
                .withdrawalAccountId(5L)
                .subscriptionAmount(1_000_000L)
                .termMonths((short) 12)
                .paymentDay((byte) 15)
                .baseRate(new BigDecimal("2.50"))
                .preferentialRate(new BigDecimal("0.30"))
                .appliedRate(new BigDecimal("2.80"))
                .maturityHandling(MaturityHandling.TRANSFER)
                .expectedMaturityAmount(1_028_000L)
                .status(ProcessResultStatus.SUCCESS)
                .transactionNumber("20260801000000000001")
                .openedDate(LocalDate.of(2026, 8, 1))
                .maturityDate(LocalDate.of(2027, 8, 1))
                .subscribedAt(LocalDateTime.of(2026, 8, 1, 10, 30, 0))
                .build();
    }

    public static Long insertCustomer(JdbcTemplate jdbcTemplate, String userId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
                connection -> {
                    PreparedStatement ps = connection.prepareStatement(
                            "INSERT INTO customer (user_id, password_hash, user_name, birth_date, email, phone_number, joined_at, created_at, updated_at) "
                                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            Statement.RETURN_GENERATED_KEYS);
                    LocalDateTime now = LocalDateTime.now();
                    ps.setString(1, userId);
                    ps.setString(2, DUMMY_BCRYPT_HASH);
                    ps.setString(3, "홍길동");
                    ps.setObject(4, LocalDate.of(1990, 1, 1));
                    ps.setString(5, userId + "@example.com");
                    ps.setString(6, "01012345678");
                    ps.setObject(7, now);
                    ps.setObject(8, now);
                    ps.setObject(9, now);
                    return ps;
                },
                keyHolder);
        return keyHolder.getKey().longValue();
    }

    public static Long insertAccount(JdbcTemplate jdbcTemplate, String accountNumber, Long customerId, Long productId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
                connection -> {
                    PreparedStatement ps = connection.prepareStatement(
                            "INSERT INTO account (account_number, customer_id, product_id, account_type, status, password_hash, opened_date, maturity_date, created_at, updated_at) "
                                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
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
                    ps.setString(6, DUMMY_BCRYPT_HASH);
                    ps.setObject(7, now);
                    if (productId == null) {
                        ps.setNull(8, java.sql.Types.DATE);
                    } else {
                        ps.setObject(8, now.toLocalDate().plusYears(1));
                    }
                    ps.setObject(9, now);
                    ps.setObject(10, now);
                    return ps;
                },
                keyHolder);
        return keyHolder.getKey().longValue();
    }
}
