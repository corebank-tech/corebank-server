package com.shinhan.corebank.common.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.IntegrationTestSupport;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class Phase2BulkSeedServiceIntegrationTest extends IntegrationTestSupport {

    private static final Phase2MinimumSeedSpec MINIMUM_SPEC = new Phase2MinimumSeedSpec(
            100,
            300,
            500,
            0,
            0,
            6_000_001L,
            60_000_001L,
            60_000_001L,
            60_000_001L,
            60_000_001L,
            6_000_000_001L,
            "860",
            LocalDateTime.of(2026, 9, 1, 0, 0));

    private static final Phase2BulkSeedSpec BULK_SPEC = new Phase2BulkSeedSpec(
            100,
            300,
            200,
            200,
            10,
            10,
            2,
            8,
            2,
            50,
            10,
            11_000_001L,
            110_000_001L,
            110_000_001L,
            110_000_001L,
            110_000_001L,
            110_000_001L,
            110_000_001L,
            110_000_001L,
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 10, 5));

    @Autowired
    private Phase2MinimumSeedService minimumSeedService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private Clock clock;

    @BeforeEach
    @AfterEach
    void cleanSeedRanges() {
        // 실제 커밋·재개를 검증하므로 각 테스트 전후에 전용 ID 대역만 명시적으로 정리한다.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.update("DELETE FROM gl_journal_entry WHERE journal_entry_id BETWEEN 110000001 AND 110999999");
            jdbc.update(
                    "DELETE voucher FROM gl_voucher voucher JOIN transfer seeded ON seeded.transaction_number=voucher.description WHERE seeded.transfer_id BETWEEN 110000001 AND 110999999");
            jdbc.update(
                    "DELETE voucher FROM gl_voucher voucher JOIN ledger_entry seeded ON seeded.transaction_number=voucher.description WHERE seeded.ledger_entry_id BETWEEN 110000001 AND 110999999");
            jdbc.update("DELETE FROM ledger_entry WHERE ledger_entry_id BETWEEN 110000001 AND 110999999");
            jdbc.update("DELETE FROM product_subscription WHERE subscription_id BETWEEN 110000001 AND 110999999");
            jdbc.update("DELETE FROM transfer WHERE transfer_id BETWEEN 110000001 AND 110999999");
            jdbc.update("DELETE FROM scheduled_transfer WHERE scheduled_transfer_id BETWEEN 110000001 AND 110999999");
            jdbc.update("DELETE FROM auto_transfer WHERE auto_transfer_id BETWEEN 110000001 AND 110999999");
            jdbc.update("DELETE FROM account WHERE account_id BETWEEN 110000001 AND 110999999");
            jdbc.update("DELETE FROM transfer_limit WHERE customer_id BETWEEN 11000001 AND 11099999");
            jdbc.update("DELETE FROM customer WHERE customer_id BETWEEN 11000001 AND 11099999");
            jdbc.update("DELETE FROM gl_journal_entry WHERE journal_entry_id BETWEEN 6000000001 AND 6000999999");
            jdbc.update("DELETE FROM gl_journal_entry WHERE journal_entry_id BETWEEN 119000001 AND 119999999");
            jdbc.update(
                    "DELETE voucher FROM gl_voucher voucher JOIN transfer seeded ON seeded.transaction_number=voucher.description WHERE seeded.transfer_id BETWEEN 60000001 AND 60999999");
            jdbc.update("DELETE FROM ledger_entry WHERE ledger_entry_id BETWEEN 60000001 AND 60999999");
            jdbc.update("DELETE FROM transfer WHERE transfer_id BETWEEN 60000001 AND 60999999");
            jdbc.update("DELETE FROM auto_transfer WHERE auto_transfer_id BETWEEN 60000001 AND 60999999");
            jdbc.update("DELETE FROM account WHERE account_id BETWEEN 60000001 AND 60999999");
            jdbc.update("DELETE FROM transfer_limit WHERE customer_id BETWEEN 6000001 AND 6099999");
            jdbc.update("DELETE FROM customer WHERE customer_id BETWEEN 6000001 AND 6099999");
            jdbc.update("DELETE FROM gl_journal_entry WHERE voucher_no='20260901-OPN-000001'");
            jdbc.update("DELETE FROM gl_voucher WHERE voucher_no='20260901-OPN-000001'");
        });
    }

    @Test
    @DisplayName("축소 PH-60b 시드는 거래·가입·원장·전표·분개를 같은 구간에 생성한다")
    void seedsScaledBulkData() {
        minimumSeedService.seed(MINIMUM_SPEC);
        Phase2BulkSeedService service = new TestBulkSeedService(jdbc, transactionManager, clock);

        Phase2BulkSeedReport report = service.seed(BULK_SPEC);

        assertThat(report.customers()).isEqualTo(100);
        assertThat(report.accounts()).isEqualTo(300);
        assertThat(report.transactions()).isEqualTo(400);
        assertThat(report.ledgerEntries()).isEqualTo(800);
        assertThat(report.vouchers()).isEqualTo(400);
        assertThat(report.journalEntries()).isEqualTo(800);
        assertThat(report.elapsed()).isGreaterThanOrEqualTo(java.time.Duration.ZERO);
        assertThat(count("SELECT COUNT(*) FROM transfer WHERE transfer_id BETWEEN 110000001 AND 110000200"))
                .isEqualTo(200);
        assertThat(
                        count(
                                "SELECT COUNT(*) FROM product_subscription WHERE subscription_id BETWEEN 110000001 AND 110000200"))
                .isEqualTo(200);
        assertThat(
                        count(
                                "SELECT COUNT(*) FROM product_subscription WHERE subscription_id=110000010 AND term_months=2 AND base_rate=2.70 AND applied_rate=2.70 AND expected_maturity_amount=502250 AND maturity_date='2026-11-01'"))
                .isEqualTo(1);
        assertThat(
                        count(
                                "SELECT COUNT(*) FROM product_subscription WHERE subscription_id BETWEEN 110000001 AND 110000200 AND base_rate + preferential_rate <> applied_rate"))
                .isZero();
        assertThat(
                        count(
                                "SELECT COUNT(*) FROM product_subscription WHERE subscription_id BETWEEN 110000001 AND 110000200 AND expected_maturity_amount IS NULL"))
                .isZero();
        assertThat(
                        count(
                                "SELECT COUNT(*) FROM transfer seeded JOIN ledger_entry withdrawal ON withdrawal.transfer_id=seeded.transfer_id AND withdrawal.direction='WITHDRAWAL' WHERE seeded.transfer_id BETWEEN 60000001 AND 60000100 AND seeded.withdrawal_balance_after<>withdrawal.balance_after"))
                .isZero();
        assertThat(
                        count(
                                "SELECT COUNT(*) FROM transfer seeded LEFT JOIN account deposit ON deposit.account_id=seeded.deposit_account_id WHERE seeded.transfer_id BETWEEN 110000001 AND 110000200 AND (seeded.deposit_account_number IS NULL OR seeded.deposit_account_number<>deposit.account_number)"))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM ledger_entry WHERE ledger_entry_id BETWEEN 110000001 AND 110000800"))
                .isEqualTo(800);
        assertThat(count(
                        "SELECT COUNT(*) FROM gl_journal_entry WHERE journal_entry_id BETWEEN 110470001 AND 110470800"))
                .isEqualTo(800);
        assertThat(
                        count(
                                "SELECT COUNT(*) FROM account WHERE account_id BETWEEN 110000001 AND 110000300 AND account_number NOT LIKE '088%'"))
                .isZero();
        assertThat(
                        count(
                                "SELECT COUNT(*) FROM customer WHERE customer_id BETWEEN 11000001 AND 11000100 AND user_id NOT REGEXP '^[a-z][a-z0-9]{5,15}$'"))
                .isZero();
        assertThat(
                        count(
                                "SELECT COUNT(*) FROM customer customer_row JOIN account account_row ON account_row.customer_id=customer_row.customer_id WHERE customer_row.customer_id BETWEEN 11000001 AND 11000100 AND customer_row.joined_at>account_row.created_at"))
                .isZero();
        assertThat(
                        count(
                                """
                    SELECT COUNT(*) FROM (
                        SELECT account_id, MAX(trade_date) AS latest
                        FROM ledger_entry
                        WHERE account_id IN (110000001, 110000004)
                        GROUP BY account_id
                        HAVING latest <> '2026-09-01'
                    ) broken
                    """))
                .isZero();
        assertThat(
                        count(
                                """
                    SELECT COUNT(*) FROM (
                        SELECT voucher_no FROM gl_journal_entry
                        WHERE journal_entry_id BETWEEN 110470001 AND 110470800
                        GROUP BY voucher_no
                        HAVING COUNT(*) <> 2
                           OR SUM(CASE WHEN dr_cr='DEBIT' THEN amount ELSE -amount END) <> 0
                    ) broken
                    """))
                .isZero();
    }

    @Test
    @DisplayName("두 번째 거래 구간 실패 후 첫 구간은 남고 재실행은 실패 구간부터 이어진다")
    void resumesFromLastCommittedChunk() {
        minimumSeedService.seed(MINIMUM_SPEC);
        Phase2BulkSeedService failing = new FailSecondTransferChunkService(jdbc, transactionManager, clock);

        assertThatThrownBy(() -> failing.seed(BULK_SPEC)).hasMessageContaining("의도한 두 번째 구간 실패");
        assertThat(count("SELECT COUNT(*) FROM transfer WHERE transfer_id BETWEEN 110000001 AND 110000200"))
                .isEqualTo(50);
        assertThat(count("SELECT COUNT(*) FROM ledger_entry WHERE ledger_entry_id BETWEEN 110000001 AND 110000400"))
                .isEqualTo(100);
        assertThat(count(
                        "SELECT COUNT(*) FROM gl_journal_entry WHERE journal_entry_id BETWEEN 110470001 AND 110470400"))
                .isEqualTo(100);

        new TestBulkSeedService(jdbc, transactionManager, clock).seed(BULK_SPEC);

        assertThat(count("SELECT COUNT(*) FROM transfer WHERE transfer_id BETWEEN 110000001 AND 110000200"))
                .isEqualTo(200);
        assertThat(count("SELECT COUNT(*) FROM ledger_entry WHERE ledger_entry_id BETWEEN 110000001 AND 110000400"))
                .isEqualTo(400);
    }

    @Test
    @DisplayName("PH-60 전표 보완 INSERT SELECT를 실제 MySQL에서 실행한다")
    void backfillsMinimumSeedGlWithRealSql() {
        minimumSeedService.seed(MINIMUM_SPEC);
        Phase2BulkSeedService service = new TestBulkSeedService(jdbc, transactionManager, clock);

        service.backfillTransferGl(60_000_001L, MINIMUM_SPEC.transferCount(), 119_000_001L);

        assertThat(
                        count(
                                "SELECT COUNT(*) FROM gl_voucher voucher JOIN transfer seeded ON seeded.transaction_number=voucher.description WHERE seeded.transfer_id BETWEEN 60000001 AND 60000100"))
                .isEqualTo(100);
        assertThat(count(
                        "SELECT COUNT(*) FROM gl_journal_entry WHERE journal_entry_id BETWEEN 119000001 AND 119000200"))
                .isEqualTo(200);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private static class TestBulkSeedService extends Phase2BulkSeedService {

        private TestBulkSeedService(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, Clock clock) {
            super(jdbc, transactionManager, clock);
        }

        @Override
        void validatePrerequisites() {
            // 축소 통합 테스트는 PH-60 운영 건수 대신 같은 스키마의 최소 선행 데이터만 사용한다.
        }

        @Override
        void backfillPhase60Gl(Phase2BulkSeedSpec spec) {
            // PH-60 보완은 운영 고정 대역 SQL이므로 축소 생성 테스트에서 제외한다.
        }

        @Override
        void advanceSequences(Phase2BulkSeedSpec spec) {
            // 테스트 전용 ID 대역 밖의 실제 자동 채번 값을 변경하지 않도록 ALTER TABLE을 제외한다.
        }
    }

    private static final class FailSecondTransferChunkService extends TestBulkSeedService {

        private int chunks;

        private FailSecondTransferChunkService(
                JdbcTemplate jdbc, PlatformTransactionManager transactionManager, Clock clock) {
            super(jdbc, transactionManager, clock);
        }

        @Override
        void afterTransferCompanionsInserted(int offset) {
            if (++chunks == 2) {
                throw new IllegalStateException("의도한 두 번째 구간 실패");
            }
        }
    }
}
