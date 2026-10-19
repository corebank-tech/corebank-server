package com.shinhan.corebank.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.business.support.BusinessDateTestFixture;
import com.shinhan.corebank.transfer.application.port.in.ProductSubscriptionDepositUseCase;
import com.shinhan.corebank.transfer.application.port.in.ProductSubscriptionDepositUseCase.ProductSubscriptionDepositCommand;
import com.shinhan.corebank.transfer.application.port.in.ProductSubscriptionDepositUseCase.ProductSubscriptionDepositResult;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 상품가입 초입금 통합 테스트. 실제 GL 훅이 붙어 커밋되므로 @Transactional을 두지 않고 직접 정리한다.
 */
@DisplayName("ProductSubscriptionDepositService — 상품가입 초입금 기표")
class ProductSubscriptionDepositServiceTest extends IntegrationTestSupport {

    // 시드·다른 테스트와 겹치지 않게 이슈 번호 대역을 쓴다.
    private static final long CUSTOMER_ID = 516L;
    private static final long WITHDRAWAL_ACCOUNT = 51601L;
    private static final long SUBSCRIPTION_ACCOUNT = 51602L;
    // GL 전표 채번이 거래일별이라 다른 테스트가 쓰지 않는 영업일을 고른다.
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2032, 3, 2);

    @Autowired
    private ProductSubscriptionDepositUseCase productSubscriptionDepositUseCase;

    @Autowired
    private BusinessDateTestFixture businessDateTestFixture;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private LocalDate originalBusinessDate;

    @BeforeEach
    void setUp() {
        cleanUp();
        seed();
        originalBusinessDate = businessDateTestFixture.currentInDb();
        businessDateTestFixture.moveTo(BUSINESS_DATE);
    }

    @AfterEach
    void tearDown() {
        businessDateTestFixture.moveTo(originalBusinessDate);
        cleanUp();
    }

    @Test
    @DisplayName("원장 2행과 훅 B가 세운 GL 전표가 모두 현재 영업일을 거래일로 갖는다")
    void deposit_tradeDateIsBusinessDate_onLedgerAndVoucher() {
        // when
        ProductSubscriptionDepositResult result = productSubscriptionDepositUseCase.deposit(
                new ProductSubscriptionDepositCommand(WITHDRAWAL_ACCOUNT, SUBSCRIPTION_ACCOUNT, 3_000L));

        // then
        assertThat(jdbcTemplate.queryForList(
                        "SELECT trade_date FROM ledger_entry WHERE transaction_number = ?",
                        LocalDate.class,
                        result.transactionNumber()))
                .containsExactly(BUSINESS_DATE, BUSINESS_DATE);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT trade_date FROM gl_voucher WHERE reference_key = ?",
                        LocalDate.class,
                        result.transactionNumber()))
                .isEqualTo(BUSINESS_DATE);
    }

    private void seed() {
        jdbcTemplate.update(
                """
            INSERT INTO customer (customer_id, user_id, password_hash, user_name, birth_date, email, phone_number, joined_at, created_at, updated_at)
            VALUES (?, 'issue516', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', '테스터', '1990-01-01', 'issue516@test.com', '01051600000', NOW(6), NOW(6), NOW(6))
            """,
                CUSTOMER_ID);
        jdbcTemplate.update(
                """
            INSERT INTO account (account_id, account_number, customer_id, product_id, account_type, balance, status, password_hash, withdrawal_registered, withdrawal_registered_at, opened_date, created_at, updated_at)
            VALUES (?, '516000000001', ?, NULL, 'DEMAND_DEPOSIT', 10000, 'ACTIVE', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', TRUE, NOW(6), '2026-08-01', NOW(6), NOW(6)),
                   (?, '516000000002', ?, NULL, 'DEMAND_DEPOSIT', 0, 'ACTIVE', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', FALSE, NULL, '2026-08-01', NOW(6), NOW(6))
            """,
                WITHDRAWAL_ACCOUNT,
                CUSTOMER_ID,
                SUBSCRIPTION_ACCOUNT,
                CUSTOMER_ID);
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM gl_journal_entry WHERE trade_date = ?", BUSINESS_DATE);
        jdbcTemplate.update("DELETE FROM gl_voucher WHERE trade_date = ?", BUSINESS_DATE);
        jdbcTemplate.update("DELETE FROM gl_voucher_sequence WHERE trade_date = ?", BUSINESS_DATE);
        jdbcTemplate.update(
                "DELETE FROM ledger_entry WHERE account_id IN (?, ?)", WITHDRAWAL_ACCOUNT, SUBSCRIPTION_ACCOUNT);
        jdbcTemplate.update("DELETE FROM account WHERE account_id IN (?, ?)", WITHDRAWAL_ACCOUNT, SUBSCRIPTION_ACCOUNT);
        jdbcTemplate.update("DELETE FROM customer WHERE customer_id = ?", CUSTOMER_ID);
    }
}
