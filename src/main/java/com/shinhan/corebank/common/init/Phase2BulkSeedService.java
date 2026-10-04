package com.shinhan.corebank.common.init;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class Phase2BulkSeedService {

    private static final String PASSWORD_HASH = "$2y$10$1NOtaTsHuD0rdffA3ReFKO5S0J4bHlVES6okQMYubUd0OuVFfMZXa";
    private static final long PH60_TRANSFER_ID_START = 60_000_001L;
    private static final int PH60_TRANSFER_COUNT = 235_000;
    private static final long PH60_ACCOUNT_ID_START = 60_000_001L;
    private static final int PH60_ACCOUNT_COUNT = 30_000;
    private static final String OPENING_VOUCHER_NUMBER = "20260901-OPN-000001";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public Phase2BulkSeedService(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public Phase2BulkSeedReport seed(Phase2BulkSeedSpec spec) {
        // 각 단계와 구간을 독립 트랜잭션으로 실행해 실패 지점부터 재개할 수 있게 한다.
        long startedAt = System.nanoTime();
        Phase2BulkSeedPlan plan = new Phase2BulkSeedPlan(spec);
        validatePrerequisites();
        runStage("customers", spec.customerCount(), customerCount(spec), () -> insertCustomers(spec, plan));
        runStage("accounts", spec.accountCount(), accountCount(spec), () -> insertAccounts(spec, plan));
        backfillPhase60Gl(spec);
        insertTransferChunks(spec, plan);
        insertSubscriptionChunks(spec, plan);
        runStage(
                "auto transfers",
                spec.autoTransferCount(),
                autoTransferCount(spec),
                () -> insertAutoTransfers(spec, plan));
        runStage(
                "scheduled transfers",
                spec.scheduledTransferCount(),
                scheduledTransferCount(spec),
                () -> insertScheduledTransfers(spec, plan));
        advanceSequences(spec);
        validate(spec);
        return new Phase2BulkSeedReport(
                spec.customerCount(),
                spec.accountCount(),
                spec.transactionCount(),
                spec.ledgerEntryCount(),
                spec.voucherCount(),
                spec.journalEntryCount(),
                spec.autoTransferCount(),
                spec.scheduledTransferCount(),
                Duration.ofNanos(System.nanoTime() - startedAt));
    }

    void validatePrerequisites() {
        requireCount(
                "PH-60 customer prerequisite",
                10_000,
                "SELECT COUNT(*) FROM customer WHERE customer_id BETWEEN 6000001 AND 6010000");
        requireCount(
                "PH-60 account prerequisite",
                PH60_ACCOUNT_COUNT,
                "SELECT COUNT(*) FROM account WHERE account_id BETWEEN ? AND ?",
                PH60_ACCOUNT_ID_START,
                PH60_ACCOUNT_ID_START + PH60_ACCOUNT_COUNT - 1);
        requireCount(
                "PH-60 transfer prerequisite",
                PH60_TRANSFER_COUNT,
                "SELECT COUNT(*) FROM transfer WHERE transfer_id BETWEEN ? AND ?",
                PH60_TRANSFER_ID_START,
                PH60_TRANSFER_ID_START + PH60_TRANSFER_COUNT - 1);
        requireCount(
                "opening voucher", 1, "SELECT COUNT(*) FROM gl_voucher WHERE voucher_no = ?", OPENING_VOUCHER_NUMBER);
        product("PRD_BASIC_DEP");
        product("PRD_SHORT_DEP");
        product("PRD_REGULAR_SAVE");
    }

    void runStage(String label, int expected, int actual, Runnable insertion) {
        if (actual == expected) {
            return;
        }
        if (actual != 0) {
            throw new IllegalStateException(
                    "PH-60b " + label + " stage is partial: expected=" + expected + ", actual=" + actual);
        }
        transaction.executeWithoutResult(status -> insertion.run());
    }

    private void insertCustomers(Phase2BulkSeedSpec spec, Phase2BulkSeedPlan plan) {
        String customerSql =
                """
                INSERT INTO customer (
                    customer_id, user_id, password_hash, user_name, birth_date, email, phone_number,
                    login_failure_count, account_locked, password_changed_at, joined_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 0, FALSE, ?, ?, ?, ?)
                """;
        batch(spec.customerCount(), spec.jdbcBatchSize(), customerSql, (statement, index) -> {
            LocalDateTime timestamp = spec.periodStart().atStartOfDay().plusSeconds(index);
            statement.setLong(1, spec.customerIdStart() + index);
            statement.setString(2, plan.customerUserId(index));
            statement.setString(3, PASSWORD_HASH);
            statement.setString(4, String.format(Locale.ROOT, "PH60B고객%05d", index + 1));
            statement.setDate(5, Date.valueOf(LocalDate.of(1970 + index % 30, index % 12 + 1, index % 28 + 1)));
            statement.setString(6, String.format(Locale.ROOT, "ph60b%05d@example.com", index + 1));
            statement.setString(7, String.format(Locale.ROOT, "011%08d", index + 1));
            setTimestamp(statement, 8, timestamp);
            setTimestamp(statement, 9, timestamp);
            setTimestamp(statement, 10, timestamp);
            setTimestamp(statement, 11, timestamp);
        });
        String limitSql =
                "INSERT INTO transfer_limit (customer_id, one_time_limit, daily_limit, created_at, updated_at) VALUES (?, 10000000, 50000000, ?, ?)";
        batch(spec.customerCount(), spec.jdbcBatchSize(), limitSql, (statement, index) -> {
            statement.setLong(1, spec.customerIdStart() + index);
            setTimestamp(statement, 2, spec.periodStart().atStartOfDay());
            setTimestamp(statement, 3, spec.periodStart().atStartOfDay());
        });
    }

    private void insertAccounts(Phase2BulkSeedSpec spec, Phase2BulkSeedPlan plan) {
        Map<String, ProductSeed> products = products();
        reserveAccountNumberRanges();
        String sql =
                """
                INSERT INTO account (
                    account_id, account_number, customer_id, product_id, account_type, balance, status,
                    password_hash, password_failure_count, password_locked, alias, display_order,
                    withdrawal_registered, withdrawal_registered_at, opened_date, maturity_date,
                    closed_date, last_transaction_at, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 0, ?, ?, 0, FALSE, NULL, ?, ?, ?, ?, ?, NULL, NULL, 0, ?, ?)
                """;
        batch(spec.accountCount(), spec.jdbcBatchSize(), sql, (statement, index) -> {
            int customerIndex = index / 3;
            int kind = index % 3;
            LocalDate openedDate = accountOpenedDate(plan, spec, customerIndex, kind);
            LocalDate maturityDate = accountMaturityDate(plan, spec, customerIndex, kind);
            statement.setLong(1, spec.accountIdStart() + index);
            statement.setString(2, plan.accountNumber(index));
            statement.setLong(3, spec.customerIdStart() + customerIndex);
            if (kind == 0) {
                statement.setNull(4, java.sql.Types.BIGINT);
            } else {
                statement.setLong(
                        4,
                        products.get(accountProductCode(plan, customerIndex, kind))
                                .id());
            }
            statement.setString(5, kind == 0 ? "DEMAND_DEPOSIT" : kind == 1 ? "TIME_DEPOSIT" : "INSTALLMENT_SAVINGS");
            statement.setString(6, kind == 1 ? plan.accountStatus(customerIndex) : "ACTIVE");
            statement.setString(7, PASSWORD_HASH);
            statement.setInt(8, kind + 1);
            statement.setBoolean(9, kind == 0);
            if (kind == 0) {
                setTimestamp(statement, 10, openedDate.atStartOfDay());
            } else {
                statement.setNull(10, java.sql.Types.TIMESTAMP);
            }
            statement.setDate(11, Date.valueOf(openedDate));
            if (maturityDate == null) {
                statement.setNull(12, java.sql.Types.DATE);
            } else {
                statement.setDate(12, Date.valueOf(maturityDate));
            }
            setTimestamp(statement, 13, openedDate.atStartOfDay());
            setTimestamp(statement, 14, spec.periodEnd().atTime(23, 59, 59));
        });
    }

    void backfillPhase60Gl(Phase2BulkSeedSpec spec) {
        int vouchers = count(
                "SELECT COUNT(*) FROM gl_voucher WHERE voucher_no BETWEEN '20260901-TRF-000001' AND '20260901-TRF-235000'");
        int journals = count(
                "SELECT COUNT(*) FROM gl_journal_entry WHERE journal_entry_id BETWEEN ? AND ?",
                spec.journalEntryIdStart(),
                spec.journalEntryIdStart() + PH60_TRANSFER_COUNT * 2L - 1);
        if (vouchers == PH60_TRANSFER_COUNT && journals == PH60_TRANSFER_COUNT * 2) {
            return;
        }
        if (vouchers != 0 || journals != 0) {
            throw new IllegalStateException("PH-60 GL 보완 구간이 부분 적재 상태입니다.");
        }
        transaction.executeWithoutResult(status -> {
            jdbc.update(
                    """
                    INSERT INTO gl_voucher (voucher_no, trade_date, tx_type, description, created_at)
                    SELECT CONCAT('20260901-TRF-', LPAD(transfer_id - 60000000, 6, '0')),
                           trade_date, 'TRANSFER', transaction_number, created_at
                    FROM transfer
                    WHERE transfer_id BETWEEN 60000001 AND 60235000
                    ORDER BY transfer_id
                    """);
            jdbc.update(
                    """
                    INSERT INTO gl_journal_entry
                        (journal_entry_id, voucher_no, line_no, account_code, dr_cr, amount, trade_date, created_at)
                    SELECT ? + (transfer_id - 60000001) * 2,
                           CONCAT('20260901-TRF-', LPAD(transfer_id - 60000000, 6, '0')),
                           1, '20100', 'DEBIT', amount, trade_date, created_at
                    FROM transfer WHERE transfer_id BETWEEN 60000001 AND 60235000
                    UNION ALL
                    SELECT ? + (transfer_id - 60000001) * 2 + 1,
                           CONCAT('20260901-TRF-', LPAD(transfer_id - 60000000, 6, '0')),
                           2, '20100', 'CREDIT', amount, trade_date, created_at
                    FROM transfer WHERE transfer_id BETWEEN 60000001 AND 60235000
                    """,
                    spec.journalEntryIdStart(),
                    spec.journalEntryIdStart());
        });
    }

    private void insertTransferChunks(Phase2BulkSeedSpec spec, Phase2BulkSeedPlan plan) {
        int completed = transferCount(spec);
        validateCompletedChunkCount("transfer", completed, spec.transferCount(), spec.transactionChunkSize());
        validateTransferCompanions(spec, completed);
        for (int offset = completed; offset < spec.transferCount(); offset += spec.transactionChunkSize()) {
            int start = offset;
            int size = Math.min(spec.transactionChunkSize(), spec.transferCount() - offset);
            transaction.executeWithoutResult(status -> insertTransferChunk(spec, plan, start, size));
        }
    }

    private void insertTransferChunk(Phase2BulkSeedSpec spec, Phase2BulkSeedPlan plan, int offset, int size) {
        Map<Long, Long> balances = lockTransferBalances(spec);
        List<TransferSeed> transfers = new ArrayList<>(size);
        Map<VoucherKey, Integer> voucherSequences = voucherSequences("TRANSFER");
        for (int local = 0; local < size; local++) {
            int index = offset + local;
            long withdrawalId = plan.withdrawalAccountId(index);
            long depositId = plan.depositAccountId(index);
            long amount = plan.transferAmount(index);
            long withdrawalAfter =
                    withdrawalBalanceAfter(balance(balances, withdrawalId), amount, withdrawalId, "자금 공급 또는 이체");
            long depositAfter = balance(balances, depositId) + amount;
            balances.put(withdrawalId, withdrawalAfter);
            balances.put(depositId, depositAfter);
            LocalDate tradeDate = plan.tradeDate(index);
            VoucherKey key = new VoucherKey(tradeDate, "TRANSFER");
            int voucherSequence = voucherSequences.merge(key, 1, Integer::sum);
            transfers.add(new TransferSeed(
                    index,
                    withdrawalId,
                    depositId,
                    amount,
                    withdrawalAfter,
                    depositAfter,
                    plan.transactionNumber(index),
                    plan.occurredAt(index),
                    voucherNumber(tradeDate, "TRF", voucherSequence)));
        }
        insertTransfers(spec, transfers);
        insertTransferLedgers(spec, transfers);
        insertVouchers(transfers.stream().map(TransferSeed::voucher).toList());
        insertTransferJournals(spec, transfers);
        updateBalances(balances, spec.jdbcBatchSize());
    }

    private void insertSubscriptionChunks(Phase2BulkSeedSpec spec, Phase2BulkSeedPlan plan) {
        int completed = subscriptionCount(spec);
        validateCompletedChunkCount("subscription", completed, spec.subscriptionCount(), spec.transactionChunkSize());
        validateSubscriptionCompanions(spec, completed);
        Map<String, ProductSeed> products = products();
        for (int offset = completed; offset < spec.subscriptionCount(); offset += spec.transactionChunkSize()) {
            int start = offset;
            int size = Math.min(spec.transactionChunkSize(), spec.subscriptionCount() - offset);
            transaction.executeWithoutResult(status -> insertSubscriptionChunk(spec, plan, products, start, size));
        }
    }

    private void insertSubscriptionChunk(
            Phase2BulkSeedSpec spec, Phase2BulkSeedPlan plan, Map<String, ProductSeed> products, int offset, int size) {
        Map<Long, Long> balances = lockNewAccountBalances(spec);
        List<SubscriptionSeed> subscriptions = new ArrayList<>(size);
        Map<VoucherKey, Integer> voucherSequences = voucherSequences("PRODUCT_SUBSCRIPTION");
        for (int local = 0; local < size; local++) {
            int index = offset + local;
            int customerIndex = plan.subscriptionCustomerIndex(index);
            long withdrawalId = plan.demandAccountId(customerIndex);
            long depositId = plan.subscriptionAccountId(index);
            long amount = plan.subscriptionAmount(index);
            long withdrawalAfter =
                    withdrawalBalanceAfter(balance(balances, withdrawalId), amount, withdrawalId, "상품가입");
            long depositAfter = balance(balances, depositId) + amount;
            balances.put(withdrawalId, withdrawalAfter);
            balances.put(depositId, depositAfter);
            LocalDate tradeDate = plan.subscriptionDate(index);
            VoucherKey key = new VoucherKey(tradeDate, "PRODUCT_SUBSCRIPTION");
            int voucherSequence = voucherSequences.merge(key, 1, Integer::sum);
            ProductSeed product = products.get(plan.productCode(index));
            BigDecimal rate = rate(product.id(), plan.termMonths(index));
            int globalIndex = spec.transferCount() + index;
            subscriptions.add(new SubscriptionSeed(
                    index,
                    customerIndex,
                    withdrawalId,
                    depositId,
                    amount,
                    withdrawalAfter,
                    depositAfter,
                    product,
                    plan.termMonths(index),
                    rate,
                    tradeDate,
                    plan.maturityDate(index),
                    plan.transactionNumber(tradeDate, globalIndex),
                    tradeDate.atTime(12, 0).plusSeconds(index % 43_200),
                    voucherNumber(tradeDate, "SUB", voucherSequence)));
        }
        insertSubscriptions(spec, subscriptions);
        insertSubscriptionLedgers(spec, subscriptions);
        insertVouchers(subscriptions.stream().map(SubscriptionSeed::voucher).toList());
        insertSubscriptionJournals(spec, subscriptions);
        updateBalances(balances, spec.jdbcBatchSize());
    }

    private void insertTransfers(Phase2BulkSeedSpec spec, List<TransferSeed> seeds) {
        String sql =
                """
                INSERT INTO transfer (
                    transfer_id, transaction_number, withdrawal_account_id, deposit_account_id,
                    deposit_account_number, payee_name, amount, fee, transfer_type, channel, status,
                    source_type, source_id, execution_date, my_passbook_memo, recipient_passbook_memo,
                    withdrawal_balance_after, error_code, error_message, transferred_at, created_at, trade_date
                ) VALUES (?, ?, ?, ?, (SELECT account_number FROM account WHERE account_id = ?),
                          'PH60B', ?, 0, 'IMMEDIATE', 'WB', 'SUCCESS', NULL, NULL, NULL,
                          'PH60B', 'PH60B', ?, NULL, NULL, ?, ?, ?)
                """;
        batchSeeds(seeds, spec.jdbcBatchSize(), sql, (statement, seed) -> {
            statement.setLong(1, spec.transferIdStart() + seed.index());
            statement.setString(2, seed.transactionNumber());
            statement.setLong(3, seed.withdrawalAccountId());
            statement.setLong(4, seed.depositAccountId());
            statement.setLong(5, seed.depositAccountId());
            statement.setLong(6, seed.amount());
            statement.setLong(7, seed.withdrawalAfter());
            setTimestamp(statement, 8, seed.occurredAt());
            setTimestamp(statement, 9, seed.occurredAt());
            statement.setDate(10, Date.valueOf(seed.occurredAt().toLocalDate()));
        });
    }

    private void insertTransferLedgers(Phase2BulkSeedSpec spec, List<TransferSeed> seeds) {
        String sql =
                """
                INSERT INTO ledger_entry (
                    ledger_entry_id, occurred_at, account_id, transfer_id, transaction_number,
                    direction, amount, balance_after, transaction_type, transaction_content,
                    channel, reversed, reversal_id, trade_date
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'IMMEDIATE_TRANSFER', 'PH60B', 'WB', FALSE, NULL, ?)
                """;
        List<LedgerSeed> ledgers = new ArrayList<>(seeds.size() * 2);
        for (TransferSeed seed : seeds) {
            long firstId = spec.ledgerEntryIdStart() + seed.index() * 2L;
            ledgers.add(new LedgerSeed(
                    firstId,
                    seed.occurredAt(),
                    seed.withdrawalAccountId(),
                    spec.transferIdStart() + seed.index(),
                    seed.transactionNumber(),
                    "WITHDRAWAL",
                    seed.amount(),
                    seed.withdrawalAfter()));
            ledgers.add(new LedgerSeed(
                    firstId + 1,
                    seed.occurredAt(),
                    seed.depositAccountId(),
                    spec.transferIdStart() + seed.index(),
                    seed.transactionNumber(),
                    "DEPOSIT",
                    seed.amount(),
                    seed.depositAfter()));
        }
        insertLedgers(ledgers, spec.jdbcBatchSize(), sql);
    }

    private void insertSubscriptions(Phase2BulkSeedSpec spec, List<SubscriptionSeed> seeds) {
        String sql =
                """
                INSERT INTO product_subscription (
                    subscription_id, customer_id, product_id, account_id, withdrawal_account_id,
                    subscription_amount, term_months, payment_day, base_rate, preferential_rate,
                    applied_rate, maturity_handling, expected_maturity_amount, status,
                    transaction_number, opened_date, maturity_date, subscribed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0.00, ?, 'TRANSFER', NULL, 'SUCCESS', ?, ?, ?, ?)
                """;
        batchSeeds(seeds, spec.jdbcBatchSize(), sql, (statement, seed) -> {
            statement.setLong(1, spec.subscriptionIdStart() + seed.index());
            statement.setLong(2, spec.customerIdStart() + seed.customerIndex());
            statement.setLong(3, seed.product().id());
            statement.setLong(4, seed.depositAccountId());
            statement.setLong(5, seed.withdrawalAccountId());
            statement.setLong(6, seed.amount());
            statement.setInt(7, seed.termMonths());
            if (seed.product().code().equals("PRD_REGULAR_SAVE")) {
                statement.setInt(8, Math.min(seed.tradeDate().getDayOfMonth(), 28));
            } else {
                statement.setNull(8, java.sql.Types.TINYINT);
            }
            statement.setBigDecimal(9, seed.product().baseRate());
            statement.setBigDecimal(10, seed.appliedRate());
            statement.setString(11, seed.transactionNumber());
            statement.setDate(12, Date.valueOf(seed.tradeDate()));
            statement.setDate(13, Date.valueOf(seed.maturityDate()));
            setTimestamp(statement, 14, seed.occurredAt());
        });
    }

    private void insertSubscriptionLedgers(Phase2BulkSeedSpec spec, List<SubscriptionSeed> seeds) {
        String sql =
                """
                INSERT INTO ledger_entry (
                    ledger_entry_id, occurred_at, account_id, transfer_id, transaction_number,
                    direction, amount, balance_after, transaction_type, transaction_content,
                    channel, reversed, reversal_id, trade_date
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PRODUCT_SUBSCRIPTION', 'PH60B가입', 'WB', FALSE, NULL, ?)
                """;
        List<LedgerSeed> ledgers = new ArrayList<>(seeds.size() * 2);
        for (SubscriptionSeed seed : seeds) {
            long firstId = spec.ledgerEntryIdStart() + spec.transferCount() * 2L + seed.index() * 2L;
            ledgers.add(new LedgerSeed(
                    firstId,
                    seed.occurredAt(),
                    seed.withdrawalAccountId(),
                    null,
                    seed.transactionNumber(),
                    "WITHDRAWAL",
                    seed.amount(),
                    seed.withdrawalAfter()));
            ledgers.add(new LedgerSeed(
                    firstId + 1,
                    seed.occurredAt(),
                    seed.depositAccountId(),
                    null,
                    seed.transactionNumber(),
                    "DEPOSIT",
                    seed.amount(),
                    seed.depositAfter()));
        }
        insertLedgers(ledgers, spec.jdbcBatchSize(), sql);
    }

    private void insertLedgers(List<LedgerSeed> seeds, int batchSize, String sql) {
        batchSeeds(seeds, batchSize, sql, (statement, seed) -> {
            statement.setLong(1, seed.id());
            setTimestamp(statement, 2, seed.occurredAt());
            statement.setLong(3, seed.accountId());
            if (seed.transferId() == null) {
                statement.setNull(4, java.sql.Types.BIGINT);
            } else {
                statement.setLong(4, seed.transferId());
            }
            statement.setString(5, seed.transactionNumber());
            statement.setString(6, seed.direction());
            statement.setLong(7, seed.amount());
            statement.setLong(8, seed.balanceAfter());
            statement.setDate(9, Date.valueOf(seed.occurredAt().toLocalDate()));
        });
    }

    private void insertVouchers(List<VoucherSeed> seeds) {
        String sql =
                "INSERT INTO gl_voucher (voucher_no, trade_date, tx_type, description, created_at) VALUES (?, ?, ?, ?, ?)";
        batchSeeds(seeds, 2_000, sql, (statement, seed) -> {
            statement.setString(1, seed.number());
            statement.setDate(2, Date.valueOf(seed.tradeDate()));
            statement.setString(3, seed.type());
            statement.setString(4, seed.description());
            setTimestamp(statement, 5, seed.createdAt());
        });
    }

    private void insertTransferJournals(Phase2BulkSeedSpec spec, List<TransferSeed> seeds) {
        long start = spec.journalEntryIdStart() + PH60_TRANSFER_COUNT * 2L;
        List<JournalSeed> journals = new ArrayList<>(seeds.size() * 2);
        for (TransferSeed seed : seeds) {
            long id = start + seed.index() * 2L;
            journals.add(new JournalSeed(
                    id,
                    seed.voucher().number(),
                    1,
                    "DEBIT",
                    seed.amount(),
                    seed.voucher().tradeDate()));
            journals.add(new JournalSeed(
                    id + 1,
                    seed.voucher().number(),
                    2,
                    "CREDIT",
                    seed.amount(),
                    seed.voucher().tradeDate()));
        }
        insertJournals(journals, spec.jdbcBatchSize());
    }

    private void insertSubscriptionJournals(Phase2BulkSeedSpec spec, List<SubscriptionSeed> seeds) {
        long start = spec.journalEntryIdStart() + PH60_TRANSFER_COUNT * 2L + spec.transferCount() * 2L;
        List<JournalSeed> journals = new ArrayList<>(seeds.size() * 2);
        for (SubscriptionSeed seed : seeds) {
            long id = start + seed.index() * 2L;
            journals.add(new JournalSeed(
                    id,
                    seed.voucher().number(),
                    1,
                    "DEBIT",
                    seed.amount(),
                    seed.voucher().tradeDate()));
            journals.add(new JournalSeed(
                    id + 1,
                    seed.voucher().number(),
                    2,
                    "CREDIT",
                    seed.amount(),
                    seed.voucher().tradeDate()));
        }
        insertJournals(journals, spec.jdbcBatchSize());
    }

    private void insertJournals(List<JournalSeed> seeds, int batchSize) {
        String sql =
                "INSERT INTO gl_journal_entry (journal_entry_id, voucher_no, line_no, account_code, dr_cr, amount, trade_date, created_at) VALUES (?, ?, ?, '20100', ?, ?, ?, ?)";
        batchSeeds(seeds, batchSize, sql, (statement, seed) -> {
            statement.setLong(1, seed.id());
            statement.setString(2, seed.voucherNumber());
            statement.setInt(3, seed.lineNumber());
            statement.setString(4, seed.direction());
            statement.setLong(5, seed.amount());
            statement.setDate(6, Date.valueOf(seed.tradeDate()));
            setTimestamp(statement, 7, seed.tradeDate().atStartOfDay());
        });
    }

    private void insertAutoTransfers(Phase2BulkSeedSpec spec, Phase2BulkSeedPlan plan) {
        String sql =
                """
                INSERT INTO auto_transfer (
                    auto_transfer_id, customer_id, withdrawal_account_id, deposit_account_number,
                    payee_name, amount, cycle_months, transfer_day, start_date, end_date,
                    next_execution_date, my_passbook_memo, recipient_passbook_memo, status,
                    registered_at, terminated_at, updated_at
                ) VALUES (?, ?, ?, ?, 'PH60B', ?, ?, ?, ?, ?, ?, 'PH60B', 'PH60B', 'NORMAL', ?, NULL, ?)
                """;
        batch(spec.autoTransferCount(), spec.jdbcBatchSize(), sql, (statement, index) -> {
            int source = (spec.dormantCandidateCount() + index) % spec.customerCount();
            int target = (source + 1) % spec.customerCount();
            LocalDate start = LocalDate.of(2026, 10, 1).plusDays(index % 28L);
            statement.setLong(1, spec.autoTransferIdStart() + index);
            statement.setLong(2, spec.customerIdStart() + source);
            statement.setLong(3, plan.demandAccountId(source));
            statement.setString(4, plan.accountNumber(target * 3));
            statement.setLong(5, 10_000L + index % 20 * 1_000L);
            statement.setInt(
                    6,
                    switch (index % 3) {
                        case 0 -> 1;
                        case 1 -> 3;
                        default -> 6;
                    });
            statement.setInt(7, index % 28 + 1);
            statement.setDate(8, Date.valueOf(start));
            statement.setDate(9, Date.valueOf(start.plusYears(2)));
            statement.setDate(10, Date.valueOf(start));
            setTimestamp(statement, 11, spec.periodEnd().atTime(12, 0));
            setTimestamp(statement, 12, spec.periodEnd().atTime(12, 0));
        });
    }

    private void insertScheduledTransfers(Phase2BulkSeedSpec spec, Phase2BulkSeedPlan plan) {
        String sql =
                """
                INSERT INTO scheduled_transfer (
                    scheduled_transfer_id, customer_id, withdrawal_account_id, payee_bank_code,
                    payee_account_number, payee_name, amount, scheduled_date, my_passbook_memo,
                    recipient_passbook_memo, status, transaction_number, registered_at,
                    executed_at, canceled_at, failure_reason
                ) VALUES (?, ?, ?, '088', ?, 'PH60B', ?, ?, 'PH60B', 'PH60B', 'WAITING', NULL, ?, NULL, NULL, NULL)
                """;
        batch(spec.scheduledTransferCount(), spec.jdbcBatchSize(), sql, (statement, index) -> {
            int source = (spec.dormantCandidateCount() + spec.scheduledTransferCount() + index) % spec.customerCount();
            int target = (source + 7) % spec.customerCount();
            statement.setLong(1, spec.scheduledTransferIdStart() + index);
            statement.setLong(2, spec.customerIdStart() + source);
            statement.setLong(3, plan.demandAccountId(source));
            statement.setString(4, plan.accountNumber(target * 3));
            statement.setLong(5, 20_000L + index % 30 * 1_000L);
            statement.setDate(6, Date.valueOf(LocalDate.of(2026, 10, 1).plusDays(index % 365L)));
            setTimestamp(statement, 7, spec.periodEnd().atTime(13, 0));
        });
    }

    void validate(Phase2BulkSeedSpec spec) {
        requireCount(
                "customers",
                spec.customerCount(),
                "SELECT COUNT(*) FROM customer WHERE customer_id BETWEEN ? AND ?",
                spec.customerIdStart(),
                spec.customerIdStart() + spec.customerCount() - 1);
        requireCount(
                "accounts",
                spec.accountCount(),
                "SELECT COUNT(*) FROM account WHERE account_id BETWEEN ? AND ?",
                spec.accountIdStart(),
                spec.accountIdStart() + spec.accountCount() - 1);
        requireCount(
                "transfers",
                spec.transferCount(),
                "SELECT COUNT(*) FROM transfer WHERE transfer_id BETWEEN ? AND ?",
                spec.transferIdStart(),
                spec.transferIdStart() + spec.transferCount() - 1);
        requireCount(
                "subscriptions",
                spec.subscriptionCount(),
                "SELECT COUNT(*) FROM product_subscription WHERE subscription_id BETWEEN ? AND ?",
                spec.subscriptionIdStart(),
                spec.subscriptionIdStart() + spec.subscriptionCount() - 1);
        requireCount(
                "ledgers",
                spec.ledgerEntryCount(),
                "SELECT COUNT(*) FROM ledger_entry WHERE ledger_entry_id BETWEEN ? AND ?",
                spec.ledgerEntryIdStart(),
                spec.ledgerEntryIdStart() + spec.ledgerEntryCount() - 1);
        requireCount(
                "new journals",
                spec.journalEntryCount(),
                "SELECT COUNT(*) FROM gl_journal_entry WHERE journal_entry_id BETWEEN ? AND ?",
                spec.journalEntryIdStart() + PH60_TRANSFER_COUNT * 2L,
                spec.journalEntryIdStart() + PH60_TRANSFER_COUNT * 2L + spec.journalEntryCount() - 1);
        requireZero(
                "negative balances",
                "SELECT COUNT(*) FROM account WHERE account_id BETWEEN ? AND ? AND balance < 0",
                spec.accountIdStart(),
                spec.accountIdStart() + spec.accountCount() - 1);
        requireZero(
                "unbalanced vouchers",
                """
                SELECT COUNT(*) FROM (
                    SELECT voucher_no FROM gl_journal_entry
                    WHERE journal_entry_id BETWEEN ? AND ?
                    GROUP BY voucher_no
                    HAVING COUNT(*) <> 2 OR SUM(CASE WHEN dr_cr='DEBIT' THEN amount ELSE -amount END) <> 0
                ) broken
                """,
                spec.journalEntryIdStart(),
                spec.journalEntryIdStart() + PH60_TRANSFER_COUNT * 2L + spec.journalEntryCount() - 1);
        requireCount("opening voucher", 1, "SELECT COUNT(*) FROM gl_voucher WHERE tx_type='OPENING'");
        long openingLedger =
                longValue("SELECT COALESCE(SUM(amount),0) FROM ledger_entry WHERE transaction_type='OPENING'");
        long openingCredit = longValue(
                "SELECT COALESCE(SUM(amount),0) FROM gl_journal_entry WHERE voucher_no=? AND account_code='20100' AND dr_cr='CREDIT'",
                OPENING_VOUCHER_NUMBER);
        validateOpeningBalances(openingLedger, openingCredit);
    }

    private void validateTransferCompanions(Phase2BulkSeedSpec spec, int completed) {
        requireCount(
                "completed transfer ledgers",
                completed * 2,
                "SELECT COUNT(*) FROM ledger_entry WHERE ledger_entry_id BETWEEN ? AND ?",
                spec.ledgerEntryIdStart(),
                spec.ledgerEntryIdStart() + completed * 2L - 1);
        requireCount(
                "completed transfer journals",
                completed * 2,
                "SELECT COUNT(*) FROM gl_journal_entry WHERE journal_entry_id BETWEEN ? AND ?",
                spec.journalEntryIdStart() + PH60_TRANSFER_COUNT * 2L,
                spec.journalEntryIdStart() + PH60_TRANSFER_COUNT * 2L + completed * 2L - 1);
    }

    private void validateSubscriptionCompanions(Phase2BulkSeedSpec spec, int completed) {
        long ledgerStart = spec.ledgerEntryIdStart() + spec.transferCount() * 2L;
        long journalStart = spec.journalEntryIdStart() + PH60_TRANSFER_COUNT * 2L + spec.transferCount() * 2L;
        requireCount(
                "completed subscription ledgers",
                completed * 2,
                "SELECT COUNT(*) FROM ledger_entry WHERE ledger_entry_id BETWEEN ? AND ?",
                ledgerStart,
                ledgerStart + completed * 2L - 1);
        requireCount(
                "completed subscription journals",
                completed * 2,
                "SELECT COUNT(*) FROM gl_journal_entry WHERE journal_entry_id BETWEEN ? AND ?",
                journalStart,
                journalStart + completed * 2L - 1);
    }

    void validateCompletedChunkCount(String label, int actual, int expected, int chunkSize) {
        if (actual < 0 || actual > expected || (actual != expected && actual % chunkSize != 0)) {
            throw new IllegalStateException("PH-60b " + label + " checkpoint is invalid: " + actual);
        }
    }

    private Map<Long, Long> lockTransferBalances(Phase2BulkSeedSpec spec) {
        Map<Long, Long> balances = new LinkedHashMap<>();
        jdbc.query(
                "SELECT account_id, balance FROM account WHERE account_id BETWEEN ? AND ? AND MOD(account_id - ?, 3)=0 FOR UPDATE",
                (RowCallbackHandler) result -> balances.put(result.getLong(1), result.getLong(2)),
                PH60_ACCOUNT_ID_START,
                PH60_ACCOUNT_ID_START + PH60_ACCOUNT_COUNT - 1,
                PH60_ACCOUNT_ID_START);
        balances.putAll(lockNewAccountBalances(spec));
        return balances;
    }

    private Map<Long, Long> lockNewAccountBalances(Phase2BulkSeedSpec spec) {
        Map<Long, Long> balances = new LinkedHashMap<>();
        jdbc.query(
                "SELECT account_id, balance FROM account WHERE account_id BETWEEN ? AND ? FOR UPDATE",
                (RowCallbackHandler) result -> balances.put(result.getLong(1), result.getLong(2)),
                spec.accountIdStart(),
                spec.accountIdStart() + spec.accountCount() - 1);
        return balances;
    }

    private void updateBalances(Map<Long, Long> balances, int batchSize) {
        List<Map.Entry<Long, Long>> entries = new ArrayList<>(balances.entrySet());
        batchSeeds(
                entries,
                batchSize,
                "UPDATE account SET balance=?, updated_at=NOW(6) WHERE account_id=?",
                (statement, entry) -> {
                    statement.setLong(1, entry.getValue());
                    statement.setLong(2, entry.getKey());
                });
    }

    private Map<VoucherKey, Integer> voucherSequences(String type) {
        Map<VoucherKey, Integer> sequences = new HashMap<>();
        jdbc.query(
                "SELECT trade_date, COUNT(*) FROM gl_voucher WHERE tx_type=? GROUP BY trade_date",
                (RowCallbackHandler) result ->
                        sequences.put(new VoucherKey(result.getDate(1).toLocalDate(), type), result.getInt(2)),
                type);
        return sequences;
    }

    private void reserveAccountNumberRanges() {
        reserveAccountNumber("DEMAND_DEPOSIT", null, "10", 6_000_000);
        reserveAccountNumber("TIME_DEPOSIT", product("PRD_BASIC_DEP").id(), "20", 6_000_000);
        reserveAccountNumber("TIME_DEPOSIT", product("PRD_SHORT_DEP").id(), "23", 6_000_000);
        reserveAccountNumber("INSTALLMENT_SAVINGS", product("PRD_REGULAR_SAVE").id(), "32", 6_000_000);
    }

    private void reserveAccountNumber(String type, Long productId, String prefix, int minimum) {
        jdbc.update(
                """
                INSERT INTO account_number_sequence
                    (bank_code, account_type, product_id, product_prefix, last_sequence, created_at, updated_at)
                VALUES ('088', ?, ?, ?, ?, NOW(6), NOW(6))
                ON DUPLICATE KEY UPDATE last_sequence=GREATEST(last_sequence, VALUES(last_sequence))
                """,
                type,
                productId,
                prefix,
                minimum);
    }

    void advanceSequences(Phase2BulkSeedSpec spec) {
        transaction.executeWithoutResult(status -> {
            advanceAutoIncrement("customer", spec.customerIdStart() + spec.customerCount());
            advanceAutoIncrement("account", spec.accountIdStart() + spec.accountCount());
            advanceAutoIncrement("transfer", spec.transferIdStart() + spec.transferCount());
            advanceAutoIncrement("product_subscription", spec.subscriptionIdStart() + spec.subscriptionCount());
            advanceAutoIncrement("auto_transfer", spec.autoTransferIdStart() + spec.autoTransferCount());
            advanceAutoIncrement("scheduled_transfer", spec.scheduledTransferIdStart() + spec.scheduledTransferCount());
            advanceAutoIncrement(
                    "gl_journal_entry",
                    spec.journalEntryIdStart() + PH60_TRANSFER_COUNT * 2L + spec.journalEntryCount());
            jdbc.update(
                    "INSERT IGNORE INTO ledger_entry_id_sequence(sequence_id) VALUES (?)",
                    spec.ledgerEntryIdStart() + spec.ledgerEntryCount() - 1);
            jdbc.update(
                    "UPDATE account_number_sequence SET last_sequence=GREATEST(last_sequence, 6050000) WHERE bank_code='088' AND product_prefix IN ('10','32')");
            jdbc.update(
                    "UPDATE account_number_sequence SET last_sequence=GREATEST(last_sequence, 6048000) WHERE bank_code='088' AND product_prefix='20'");
            jdbc.update(
                    "UPDATE account_number_sequence SET last_sequence=GREATEST(last_sequence, 6002000) WHERE bank_code='088' AND product_prefix='23'");
            jdbc.update(
                    """
                    UPDATE account a
                    JOIN (
                        SELECT account_id, MAX(occurred_at) AS latest
                        FROM ledger_entry
                        WHERE account_id BETWEEN ? AND ?
                        GROUP BY account_id
                    ) le ON le.account_id=a.account_id
                    SET a.last_transaction_at=le.latest
                    """,
                    spec.accountIdStart(),
                    spec.accountIdStart() + spec.accountCount() - 1);
        });
    }

    private void advanceAutoIncrement(String table, long nextValue) {
        jdbc.execute("ALTER TABLE " + table + " AUTO_INCREMENT = " + nextValue);
    }

    private Map<String, ProductSeed> products() {
        return Map.of(
                "PRD_BASIC_DEP", product("PRD_BASIC_DEP"),
                "PRD_SHORT_DEP", product("PRD_SHORT_DEP"),
                "PRD_REGULAR_SAVE", product("PRD_REGULAR_SAVE"));
    }

    private ProductSeed product(String code) {
        try {
            return jdbc.queryForObject(
                    "SELECT product_id, product_code, base_rate FROM product WHERE product_code=?",
                    (result, row) -> new ProductSeed(result.getLong(1), result.getString(2), result.getBigDecimal(3)),
                    code);
        } catch (EmptyResultDataAccessException exception) {
            throw new IllegalStateException("PH-60b 필수 상품이 없습니다: " + code, exception);
        }
    }

    BigDecimal rate(long productId, int termMonths) {
        try {
            return jdbc.queryForObject(
                    "SELECT rate FROM product_rate_tier WHERE product_id=? AND term_months=?",
                    BigDecimal.class,
                    productId,
                    termMonths);
        } catch (EmptyResultDataAccessException exception) {
            throw new IllegalStateException(
                    "PH-60b 상품 기간 금리가 없습니다: productId=" + productId + ", term=" + termMonths, exception);
        }
    }

    private LocalDate accountOpenedDate(Phase2BulkSeedPlan plan, Phase2BulkSeedSpec spec, int customer, int kind) {
        if (kind == 0) {
            return spec.periodStart();
        }
        int subscriptionIndex = kind == 1 ? customer : spec.customerCount() + customer;
        return plan.subscriptionDate(subscriptionIndex);
    }

    private LocalDate accountMaturityDate(Phase2BulkSeedPlan plan, Phase2BulkSeedSpec spec, int customer, int kind) {
        if (kind == 0) {
            return null;
        }
        int subscriptionIndex = kind == 1 ? customer : spec.customerCount() + customer;
        return plan.maturityDate(subscriptionIndex);
    }

    private String accountProductCode(Phase2BulkSeedPlan plan, int customer, int kind) {
        return kind == 1 ? plan.productCode(customer) : "PRD_REGULAR_SAVE";
    }

    long balance(Map<Long, Long> balances, long accountId) {
        Long value = balances.get(accountId);
        if (value == null) {
            throw new IllegalStateException("PH-60b 대상 계좌를 찾을 수 없습니다: " + accountId);
        }
        return value;
    }

    long withdrawalBalanceAfter(long currentBalance, long amount, long accountId, String context) {
        long balanceAfter = currentBalance - amount;
        if (balanceAfter < 0) {
            throw new IllegalStateException("PH-60b " + context + " 출금계좌 잔액이 부족합니다: " + accountId);
        }
        return balanceAfter;
    }

    void validateOpeningBalances(long openingLedger, long openingCredit) {
        if (openingLedger != openingCredit) {
            throw new IllegalStateException("PH-60b OPENING 원장 합계와 예수금 대변이 일치하지 않습니다.");
        }
    }

    String voucherNumber(LocalDate date, String type, int sequence) {
        if (sequence > 999_999) {
            throw new IllegalStateException("PH-60b 전표번호 일련번호가 6자리를 초과했습니다.");
        }
        return date.toString().replace("-", "") + "-" + type + "-" + String.format(Locale.ROOT, "%06d", sequence);
    }

    private int customerCount(Phase2BulkSeedSpec spec) {
        return rangeCount(
                "customer", "customer_id", spec.customerIdStart(), spec.customerIdStart() + spec.customerCount() - 1);
    }

    private int accountCount(Phase2BulkSeedSpec spec) {
        return rangeCount(
                "account", "account_id", spec.accountIdStart(), spec.accountIdStart() + spec.accountCount() - 1);
    }

    private int transferCount(Phase2BulkSeedSpec spec) {
        return rangeCount(
                "transfer", "transfer_id", spec.transferIdStart(), spec.transferIdStart() + spec.transferCount() - 1);
    }

    private int subscriptionCount(Phase2BulkSeedSpec spec) {
        return rangeCount(
                "product_subscription",
                "subscription_id",
                spec.subscriptionIdStart(),
                spec.subscriptionIdStart() + spec.subscriptionCount() - 1);
    }

    private int autoTransferCount(Phase2BulkSeedSpec spec) {
        return rangeCount(
                "auto_transfer",
                "auto_transfer_id",
                spec.autoTransferIdStart(),
                spec.autoTransferIdStart() + spec.autoTransferCount() - 1);
    }

    private int scheduledTransferCount(Phase2BulkSeedSpec spec) {
        return rangeCount(
                "scheduled_transfer",
                "scheduled_transfer_id",
                spec.scheduledTransferIdStart(),
                spec.scheduledTransferIdStart() + spec.scheduledTransferCount() - 1);
    }

    int rangeCount(String table, String column, long start, long end) {
        RangeProgress progress = jdbc.queryForObject(
                "SELECT MIN(" + column + "), MAX(" + column + "), COUNT(*) FROM " + table + " WHERE " + column
                        + " BETWEEN ? AND ?",
                (result, row) -> new RangeProgress(result.getLong(1), result.getLong(2), result.getInt(3)),
                start,
                end);
        if (progress == null || progress.count() == 0) {
            return 0;
        }
        if (progress.minimum() != start || progress.maximum() != start + progress.count() - 1) {
            throw new IllegalStateException("PH-60b " + table + " ID 대역에 누락된 체크포인트가 있습니다.");
        }
        return progress.count();
    }

    private void requireCount(String label, int expected, String sql, Object... args) {
        int actual = count(sql, args);
        if (actual != expected) {
            throw new IllegalStateException(
                    "PH-60b " + label + " count mismatch: expected=" + expected + ", actual=" + actual);
        }
    }

    private void requireZero(String label, String sql, Object... args) {
        requireCount(label, 0, sql, args);
    }

    int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    long longValue(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private void batch(int total, int batchSize, String sql, IndexedBinder binder) {
        for (int offset = 0; offset < total; offset += batchSize) {
            int batchOffset = offset;
            int size = Math.min(batchSize, total - offset);
            jdbc.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement statement, int index) throws SQLException {
                    binder.bind(statement, batchOffset + index);
                }

                @Override
                public int getBatchSize() {
                    return size;
                }
            });
        }
    }

    private <T> void batchSeeds(List<T> seeds, int batchSize, String sql, SeedBinder<T> binder) {
        for (int offset = 0; offset < seeds.size(); offset += batchSize) {
            List<T> chunk = seeds.subList(offset, Math.min(offset + batchSize, seeds.size()));
            jdbc.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement statement, int index) throws SQLException {
                    binder.bind(statement, chunk.get(index));
                }

                @Override
                public int getBatchSize() {
                    return chunk.size();
                }
            });
        }
    }

    private void setTimestamp(PreparedStatement statement, int index, LocalDateTime value) throws SQLException {
        statement.setTimestamp(index, Timestamp.valueOf(value));
    }

    @FunctionalInterface
    private interface IndexedBinder {
        void bind(PreparedStatement statement, int index) throws SQLException;
    }

    @FunctionalInterface
    private interface SeedBinder<T> {
        void bind(PreparedStatement statement, T seed) throws SQLException;
    }

    record ProductSeed(long id, String code, BigDecimal baseRate) {}

    private record VoucherKey(LocalDate date, String type) {}

    record RangeProgress(long minimum, long maximum, int count) {}

    private record VoucherSeed(
            String number, LocalDate tradeDate, String type, String description, LocalDateTime createdAt) {}

    private record TransferSeed(
            int index,
            long withdrawalAccountId,
            long depositAccountId,
            long amount,
            long withdrawalAfter,
            long depositAfter,
            String transactionNumber,
            LocalDateTime occurredAt,
            String voucherNumber) {
        VoucherSeed voucher() {
            return new VoucherSeed(voucherNumber, occurredAt.toLocalDate(), "TRANSFER", transactionNumber, occurredAt);
        }
    }

    private record SubscriptionSeed(
            int index,
            int customerIndex,
            long withdrawalAccountId,
            long depositAccountId,
            long amount,
            long withdrawalAfter,
            long depositAfter,
            ProductSeed product,
            int termMonths,
            BigDecimal appliedRate,
            LocalDate tradeDate,
            LocalDate maturityDate,
            String transactionNumber,
            LocalDateTime occurredAt,
            String voucherNumber) {
        VoucherSeed voucher() {
            return new VoucherSeed(voucherNumber, tradeDate, "PRODUCT_SUBSCRIPTION", transactionNumber, occurredAt);
        }
    }

    private record LedgerSeed(
            long id,
            LocalDateTime occurredAt,
            long accountId,
            Long transferId,
            String transactionNumber,
            String direction,
            long amount,
            long balanceAfter) {}

    private record JournalSeed(
            long id, String voucherNumber, int lineNumber, String direction, long amount, LocalDate tradeDate) {}
}
