package com.shinhan.corebank.common.init;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
    private static final int ACCOUNT_LOCK_BATCH_SIZE = 1_000;
    private static final int BALANCE_NORMALIZATION_ACCOUNT_CHUNK_SIZE = 5_000;
    private static final String OPENING_VOUCHER_NUMBER = "20260901-OPN-000001";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public Phase2BulkSeedService(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, Clock clock) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public Phase2BulkSeedReport seed(Phase2BulkSeedSpec spec) {
        // 업무 단계와 5만 거래·5천 계좌 구간을 독립 트랜잭션으로 실행해 완료 구간을 보존한다.
        long startedAt = System.nanoTime();
        Phase2BulkSeedPlan plan = new Phase2BulkSeedPlan(spec);
        validatePrerequisites();
        rejectCompletedSeed(spec);
        runStage("customers", spec.customerCount(), customerCount(spec), () -> insertCustomers(spec, plan));
        runStage("accounts", spec.accountCount(), accountCount(spec), () -> insertAccounts(spec, plan));
        backfillPhase60Gl(spec);
        insertTransferChunks(spec, plan);
        insertSubscriptionChunks(spec, plan);
        normalizeBalancesChronologically(spec);
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

    void rejectCompletedSeed(Phase2BulkSeedSpec spec) {
        // 모든 업무 데이터가 이미 있으면 성공으로 오인하지 않도록 완료 시드의 재실행을 거부한다.
        if (count(
                                "SELECT COUNT(*) FROM customer WHERE customer_id BETWEEN ? AND ?",
                                spec.customerIdStart(),
                                spec.customerIdStart() + spec.customerCount() - 1)
                        == spec.customerCount()
                && count(
                                "SELECT COUNT(*) FROM account WHERE account_id BETWEEN ? AND ?",
                                spec.accountIdStart(),
                                spec.accountIdStart() + spec.accountCount() - 1)
                        == spec.accountCount()
                && count(
                                "SELECT COUNT(*) FROM transfer WHERE transfer_id BETWEEN ? AND ?",
                                spec.transferIdStart(),
                                spec.transferIdStart() + spec.transferCount() - 1)
                        == spec.transferCount()
                && count(
                                "SELECT COUNT(*) FROM product_subscription WHERE subscription_id BETWEEN ? AND ?",
                                spec.subscriptionIdStart(),
                                spec.subscriptionIdStart() + spec.subscriptionCount() - 1)
                        == spec.subscriptionCount()
                && count(
                                "SELECT COUNT(*) FROM auto_transfer WHERE auto_transfer_id BETWEEN ? AND ?",
                                spec.autoTransferIdStart(),
                                spec.autoTransferIdStart() + spec.autoTransferCount() - 1)
                        == spec.autoTransferCount()
                && count(
                                "SELECT COUNT(*) FROM scheduled_transfer WHERE scheduled_transfer_id BETWEEN ? AND ?",
                                spec.scheduledTransferIdStart(),
                                spec.scheduledTransferIdStart() + spec.scheduledTransferCount() - 1)
                        == spec.scheduledTransferCount()) {
            throw new IllegalStateException("PH-60b seed is already complete");
        }
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
            // 고객 가입을 계좌 개설과 최초 자금 공급보다 먼저 완료된 시각으로 둔다.
            LocalDateTime timestamp =
                    spec.periodStart().minusDays(1).atStartOfDay().plusSeconds(index);
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
        reserveAccountNumberRanges(spec);
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
        backfillTransferGl(PH60_TRANSFER_ID_START, PH60_TRANSFER_COUNT, spec.journalEntryIdStart());
    }

    void backfillTransferGl(long transferIdStart, int transferCount, long journalEntryIdStart) {
        // 운영 SQL과 같은 INSERT SELECT를 축소 대역에서도 실행해 전표·분개 보완을 검증한다.
        int vouchers = count(
                "SELECT COUNT(*) FROM gl_voucher voucher JOIN transfer seeded ON seeded.transaction_number=voucher.description WHERE seeded.transfer_id BETWEEN ? AND ?",
                transferIdStart,
                transferIdStart + transferCount - 1);
        int journals = count(
                "SELECT COUNT(*) FROM gl_journal_entry WHERE journal_entry_id BETWEEN ? AND ?",
                journalEntryIdStart,
                journalEntryIdStart + transferCount * 2L - 1);
        if (vouchers == transferCount && journals == transferCount * 2) {
            return;
        }
        if (vouchers != 0 || journals != 0) {
            throw new IllegalStateException("PH-60 GL 보완 구간이 부분 적재 상태입니다.");
        }
        transaction.executeWithoutResult(status -> {
            jdbc.update(
                    """
                    INSERT INTO gl_voucher (voucher_no, trade_date, tx_type, description, created_at)
                    SELECT CONCAT(DATE_FORMAT(trade_date, '%Y%m%d'), '-TRF-', LPAD(transfer_id - ? + 1, 6, '0')),
                           trade_date, 'TRANSFER', transaction_number, created_at
                    FROM transfer
                    WHERE transfer_id BETWEEN ? AND ?
                    ORDER BY transfer_id
                    """,
                    transferIdStart, transferIdStart, transferIdStart + transferCount - 1);
            jdbc.update(
                    """
                    INSERT INTO gl_journal_entry
                        (journal_entry_id, voucher_no, line_no, account_code, dr_cr, amount, trade_date, created_at)
                    SELECT ? + (transfer_id - ?) * 2,
                           CONCAT(DATE_FORMAT(trade_date, '%Y%m%d'), '-TRF-', LPAD(transfer_id - ? + 1, 6, '0')),
                           1, '20100', 'DEBIT', amount, trade_date, created_at
                    FROM transfer WHERE transfer_id BETWEEN ? AND ?
                    UNION ALL
                    SELECT ? + (transfer_id - ?) * 2 + 1,
                           CONCAT(DATE_FORMAT(trade_date, '%Y%m%d'), '-TRF-', LPAD(transfer_id - ? + 1, 6, '0')),
                           2, '20100', 'CREDIT', amount, trade_date, created_at
                    FROM transfer WHERE transfer_id BETWEEN ? AND ?
                    """,
                    journalEntryIdStart,
                    transferIdStart,
                    transferIdStart,
                    transferIdStart,
                    transferIdStart + transferCount - 1,
                    journalEntryIdStart,
                    transferIdStart,
                    transferIdStart,
                    transferIdStart,
                    transferIdStart + transferCount - 1);
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

    void insertTransferChunk(Phase2BulkSeedSpec spec, Phase2BulkSeedPlan plan, int offset, int size) {
        Set<Long> accountIds = new java.util.TreeSet<>();
        for (int index = offset; index < offset + size; index++) {
            accountIds.add(plan.withdrawalAccountId(index));
            accountIds.add(plan.depositAccountId(index));
        }
        LockedAccounts locked = lockAccounts(accountIds);
        Map<Long, Long> balances = locked.balances();
        Set<Long> touched = new LinkedHashSet<>();
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
            touched.add(withdrawalId);
            touched.add(depositId);
            LocalDate tradeDate = plan.tradeDate(index);
            VoucherKey key = new VoucherKey(tradeDate, "TRANSFER");
            int voucherSequence = voucherSequences.merge(key, 1, Integer::sum);
            transfers.add(new TransferSeed(
                    index,
                    withdrawalId,
                    depositId,
                    locked.accountNumbers().get(depositId),
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
        afterTransferCompanionsInserted(offset);
        updateBalances(balances, touched, spec.jdbcBatchSize());
    }

    void afterTransferCompanionsInserted(int offset) {
        // 테스트가 거래·원장·전표·분개 INSERT 이후의 구간 롤백을 결정적으로 주입하는 지점이다.
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
        Set<Long> accountIds = new java.util.TreeSet<>();
        for (int index = offset; index < offset + size; index++) {
            int customerIndex = plan.subscriptionCustomerIndex(index);
            accountIds.add(plan.demandAccountId(customerIndex));
            accountIds.add(plan.subscriptionAccountId(index));
        }
        Map<Long, Long> balances = lockAccounts(accountIds).balances();
        Set<Long> touched = new LinkedHashSet<>();
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
            touched.add(withdrawalId);
            touched.add(depositId);
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
        updateBalances(balances, touched, spec.jdbcBatchSize());
    }

    void normalizeBalancesChronologically(Phase2BulkSeedSpec spec) {
        normalizeAccountRangeInChunks(PH60_ACCOUNT_ID_START, PH60_ACCOUNT_ID_START + PH60_ACCOUNT_COUNT - 1, spec);
        normalizeAccountRangeInChunks(spec.accountIdStart(), spec.accountIdStart() + spec.accountCount() - 1, spec);
    }

    private void normalizeAccountRangeInChunks(long firstAccountId, long lastAccountId, Phase2BulkSeedSpec spec) {
        for (long start = firstAccountId; start <= lastAccountId; start += BALANCE_NORMALIZATION_ACCOUNT_CHUNK_SIZE) {
            long chunkStart = start;
            long chunkEnd = Math.min(start + BALANCE_NORMALIZATION_ACCOUNT_CHUNK_SIZE - 1L, lastAccountId);
            transaction.executeWithoutResult(status -> normalizeAccountRange(chunkStart, chunkEnd, spec));
        }
    }

    private void normalizeAccountRange(long accountIdStart, long accountIdEnd, Phase2BulkSeedSpec spec) {
        // 5천 계좌 범위별로 원장·이체·계좌를 함께 정규화해 장시간 트랜잭션과 전체 롤백을 피한다.
        // 복합 PK(ledger_entry_id, occurred_at)를 모두 걸어야 파티션 하나만 찾는다. id만 걸면 파티션 19개를 매번 뒤진다(#587).
        jdbc.update(
                """
                UPDATE ledger_entry target
                JOIN (
                    SELECT ledger_entry_id, occurred_at, running_balance FROM (
                        SELECT ledger_entry_id, occurred_at,
                               SUM(CASE WHEN direction='DEPOSIT' THEN amount ELSE -amount END)
                                   OVER (PARTITION BY account_id ORDER BY occurred_at, ledger_entry_id) running_balance
                        FROM ledger_entry
                        WHERE account_id BETWEEN ? AND ?
                    ) calculated
                ) ordered ON ordered.ledger_entry_id=target.ledger_entry_id AND ordered.occurred_at=target.occurred_at
                SET target.balance_after=ordered.running_balance
                WHERE target.balance_after<>ordered.running_balance
                """,
                accountIdStart,
                accountIdEnd);
        jdbc.update(
                """
                UPDATE transfer target
                JOIN ledger_entry withdrawal
                  ON withdrawal.transfer_id=target.transfer_id
                 AND withdrawal.direction='WITHDRAWAL'
                SET target.withdrawal_balance_after=withdrawal.balance_after
                WHERE withdrawal.account_id BETWEEN ? AND ?
                  AND target.withdrawal_balance_after<>withdrawal.balance_after
                """,
                accountIdStart,
                accountIdEnd);
        jdbc.update(
                """
                UPDATE account target
                JOIN (
                    SELECT account_id,
                           SUM(CASE WHEN direction='DEPOSIT' THEN amount ELSE -amount END) ledger_balance
                    FROM ledger_entry
                    WHERE account_id BETWEEN ? AND ?
                    GROUP BY account_id
                ) calculated ON calculated.account_id=target.account_id
                SET target.balance=calculated.ledger_balance, target.updated_at=NOW(6)
                WHERE target.balance<>calculated.ledger_balance
                """,
                accountIdStart,
                accountIdEnd);
    }

    private void insertTransfers(Phase2BulkSeedSpec spec, List<TransferSeed> seeds) {
        String sql =
                """
                INSERT INTO transfer (
                    transfer_id, transaction_number, withdrawal_account_id, deposit_account_id,
                    deposit_account_number, payee_name, amount, fee, transfer_type, channel, status,
                    source_type, source_id, execution_date, my_passbook_memo, recipient_passbook_memo,
                    withdrawal_balance_after, error_code, error_message, transferred_at, created_at, trade_date
                ) VALUES (?, ?, ?, ?, ?,
                          'PH60B', ?, 0, 'IMMEDIATE', 'WB', 'SUCCESS', NULL, NULL, NULL,
                          'PH60B', 'PH60B', ?, NULL, NULL, ?, ?, ?)
                """;
        batchSeeds(seeds, spec.jdbcBatchSize(), sql, (statement, seed) -> {
            statement.setLong(1, spec.transferIdStart() + seed.index());
            statement.setString(2, seed.transactionNumber());
            statement.setLong(3, seed.withdrawalAccountId());
            statement.setLong(4, seed.depositAccountId());
            // 서브쿼리를 2,000행 배치에 넣으면 운영 RDS에서 문장마다 테이블 2,001개를 열어 구간이 30분대로 느려진다(#587).
            statement.setString(5, seed.depositAccountNumber());
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
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0.00, ?, 'TRANSFER', ?, 'SUCCESS', ?, ?, ?, ?)
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
            // 앱 가입 규칙처럼 기간별 금리를 기본금리로 쓰고 만기 예상금액도 같은 방식으로 채운다.
            statement.setBigDecimal(9, seed.appliedRate());
            statement.setBigDecimal(10, seed.appliedRate());
            statement.setLong(11, expectedMaturityAmount(seed));
            statement.setString(12, seed.transactionNumber());
            statement.setDate(13, Date.valueOf(seed.tradeDate()));
            statement.setDate(14, Date.valueOf(seed.maturityDate()));
            setTimestamp(statement, 15, seed.occurredAt());
        });
    }

    private long expectedMaturityAmount(SubscriptionSeed seed) {
        long principal;
        long weightedMonths;
        if (seed.product().code().equals("PRD_REGULAR_SAVE")) {
            principal = Math.multiplyExact(seed.amount(), seed.termMonths());
            weightedMonths = (long) seed.termMonths() * (seed.termMonths() + 1) / 2;
        } else {
            principal = seed.amount();
            weightedMonths = seed.termMonths();
        }
        long interest = BigDecimal.valueOf(seed.amount())
                .multiply(seed.appliedRate())
                .divide(BigDecimal.valueOf(100))
                .multiply(BigDecimal.valueOf(weightedMonths))
                .divide(BigDecimal.valueOf(12), 0, RoundingMode.DOWN)
                .longValueExact();
        return Math.addExact(principal, interest);
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
            // 시작일 당일을 포함해 처음 도래하는 지정 이체일을 계산해 transfer_day 계약을 지킨다.
            LocalDate start = LocalDate.now(clock).plusDays(1);
            int transferDay = index % 28 + 1;
            LocalDate firstExecutionDate = firstExecutionDate(start, transferDay);
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
            statement.setInt(7, transferDay);
            statement.setDate(8, Date.valueOf(start));
            statement.setDate(9, Date.valueOf(start.plusYears(2)));
            statement.setDate(10, Date.valueOf(firstExecutionDate));
            setTimestamp(statement, 11, spec.periodEnd().atTime(12, 0));
            setTimestamp(statement, 12, spec.periodEnd().atTime(12, 0));
        });
    }

    LocalDate firstExecutionDate(LocalDate startDate, int transferDay) {
        LocalDate candidate = startDate.withDayOfMonth(Math.min(transferDay, startDate.lengthOfMonth()));
        if (candidate.isBefore(startDate)) {
            LocalDate nextMonth = startDate.plusMonths(1).withDayOfMonth(1);
            return nextMonth.withDayOfMonth(Math.min(transferDay, nextMonth.lengthOfMonth()));
        }
        return candidate;
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
            // 예약일도 적재 다음 날 이후로 두어 WAITING 거래가 실행 누락 상태로 시작하지 않게 한다.
            statement.setDate(6, Date.valueOf(LocalDate.now(clock).plusDays(1L + index % 365L)));
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
                "negative chronological ledger balances",
                """
                SELECT COUNT(*) FROM ledger_entry
                WHERE (account_id BETWEEN ? AND ? OR account_id BETWEEN ? AND ?)
                  AND balance_after < 0
                """,
                PH60_ACCOUNT_ID_START,
                PH60_ACCOUNT_ID_START + PH60_ACCOUNT_COUNT - 1,
                spec.accountIdStart(),
                spec.accountIdStart() + spec.accountCount() - 1);
        requireZero(
                "PH-60 funding daily limit violations",
                """
                SELECT COUNT(*) FROM (
                    SELECT seeded.withdrawal_account_id
                    FROM transfer seeded
                    JOIN account source ON source.account_id=seeded.withdrawal_account_id
                    JOIN transfer_limit limits ON limits.customer_id=source.customer_id
                    WHERE seeded.trade_date=?
                      AND seeded.withdrawal_account_id BETWEEN ? AND ?
                      AND MOD(seeded.withdrawal_account_id - ?, 3)=0
                    GROUP BY seeded.withdrawal_account_id, limits.daily_limit
                    HAVING SUM(seeded.amount)>limits.daily_limit
                ) violations
                """,
                Date.valueOf(spec.periodStart()),
                PH60_ACCOUNT_ID_START,
                PH60_ACCOUNT_ID_START + PH60_ACCOUNT_COUNT - 1,
                PH60_ACCOUNT_ID_START);
        requireZero(
                "account and ledger balance mismatches",
                """
                SELECT COUNT(*) FROM account account_row
                LEFT JOIN (
                    SELECT account_id,
                           SUM(CASE WHEN direction='DEPOSIT' THEN amount ELSE -amount END) ledger_balance
                    FROM ledger_entry
                    WHERE account_id BETWEEN ? AND ? OR account_id BETWEEN ? AND ?
                    GROUP BY account_id
                ) ledger_sum ON ledger_sum.account_id=account_row.account_id
                WHERE (account_row.account_id BETWEEN ? AND ? OR account_row.account_id BETWEEN ? AND ?)
                  AND account_row.balance<>COALESCE(ledger_sum.ledger_balance, 0)
                """,
                PH60_ACCOUNT_ID_START,
                PH60_ACCOUNT_ID_START + PH60_ACCOUNT_COUNT - 1,
                spec.accountIdStart(),
                spec.accountIdStart() + spec.accountCount() - 1,
                PH60_ACCOUNT_ID_START,
                PH60_ACCOUNT_ID_START + PH60_ACCOUNT_COUNT - 1,
                spec.accountIdStart(),
                spec.accountIdStart() + spec.accountCount() - 1);
        requireZero(
                "chronological ledger balance mismatches",
                """
                SELECT COUNT(*) FROM (
                    SELECT balance_after,
                           SUM(CASE WHEN direction='DEPOSIT' THEN amount ELSE -amount END)
                               OVER (PARTITION BY account_id ORDER BY occurred_at, ledger_entry_id) expected_balance
                    FROM ledger_entry
                    WHERE account_id BETWEEN ? AND ? OR account_id BETWEEN ? AND ?
                ) ordered
                WHERE balance_after<>expected_balance
                """,
                PH60_ACCOUNT_ID_START,
                PH60_ACCOUNT_ID_START + PH60_ACCOUNT_COUNT - 1,
                spec.accountIdStart(),
                spec.accountIdStart() + spec.accountCount() - 1);
        requireZero(
                "transfer and withdrawal ledger balance mismatches",
                """
                SELECT COUNT(*)
                FROM transfer seeded
                JOIN ledger_entry withdrawal
                  ON withdrawal.transfer_id=seeded.transfer_id
                 AND withdrawal.direction='WITHDRAWAL'
                WHERE (seeded.transfer_id BETWEEN ? AND ? OR seeded.transfer_id BETWEEN ? AND ?)
                  AND seeded.withdrawal_balance_after<>withdrawal.balance_after
                """,
                spec.transferIdStart(),
                spec.transferIdStart() + spec.transferCount() - 1,
                PH60_TRANSFER_ID_START,
                PH60_TRANSFER_ID_START + PH60_TRANSFER_COUNT - 1);
        requireZero(
                "subscription product rule violations",
                """
                SELECT COUNT(*)
                FROM product_subscription subscription
                JOIN product product_row ON product_row.product_id=subscription.product_id
                LEFT JOIN product_rate_tier rate_tier
                  ON rate_tier.product_id=subscription.product_id
                 AND rate_tier.term_months=subscription.term_months
                WHERE subscription.subscription_id BETWEEN ? AND ?
                  AND (subscription.opened_date<product_row.sale_start_date
                       OR subscription.term_months NOT BETWEEN product_row.min_term_months AND product_row.max_term_months
                       OR subscription.subscription_amount NOT BETWEEN product_row.min_amount AND product_row.max_amount
                       OR MOD(subscription.subscription_amount, product_row.amount_unit)<>0
                       OR rate_tier.rate IS NULL
                       OR subscription.base_rate<>rate_tier.rate
                       OR subscription.base_rate+subscription.preferential_rate<>subscription.applied_rate
                       OR subscription.expected_maturity_amount IS NULL)
                """,
                spec.subscriptionIdStart(),
                spec.subscriptionIdStart() + spec.subscriptionCount() - 1);
        requireZero(
                "broken transaction ledger pairs",
                """
                SELECT COUNT(*) FROM (
                    SELECT transaction_number
                    FROM ledger_entry
                    WHERE ledger_entry_id BETWEEN ? AND ?
                    GROUP BY transaction_number
                    HAVING COUNT(*)<>2
                       OR SUM(direction='WITHDRAWAL')<>1
                       OR SUM(direction='DEPOSIT')<>1
                       OR SUM(CASE WHEN direction='DEPOSIT' THEN amount ELSE -amount END)<>0
                ) broken
                """,
                spec.ledgerEntryIdStart(),
                spec.ledgerEntryIdStart() + spec.ledgerEntryCount() - 1);
        requireZero(
                "auto transfer first execution date mismatches",
                """
                SELECT COUNT(*) FROM auto_transfer
                WHERE auto_transfer_id BETWEEN ? AND ?
                  AND DAY(next_execution_date)<>transfer_day
                """,
                spec.autoTransferIdStart(),
                spec.autoTransferIdStart() + spec.autoTransferCount() - 1);
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

    private LockedAccounts lockAccounts(Set<Long> accountIds) {
        Map<Long, Long> balances = new LinkedHashMap<>();
        Map<Long, String> accountNumbers = new HashMap<>();
        List<Long> ordered = accountIds.stream().sorted().toList();
        for (int offset = 0; offset < ordered.size(); offset += ACCOUNT_LOCK_BATCH_SIZE) {
            List<Long> chunk = ordered.subList(offset, Math.min(offset + ACCOUNT_LOCK_BATCH_SIZE, ordered.size()));
            String placeholders = String.join(",", java.util.Collections.nCopies(chunk.size(), "?"));
            jdbc.query(
                    "SELECT account_id, balance, account_number FROM account WHERE account_id IN (" + placeholders
                            + ") ORDER BY account_id FOR UPDATE",
                    (RowCallbackHandler) result -> {
                        balances.put(result.getLong(1), result.getLong(2));
                        accountNumbers.put(result.getLong(1), result.getString(3));
                    },
                    chunk.toArray());
        }
        return new LockedAccounts(balances, accountNumbers);
    }

    private void updateBalances(Map<Long, Long> balances, Set<Long> touched, int batchSize) {
        // 각 구간에서 실제 입출금이 발생한 계좌만 갱신해 불필요한 잠금과 UPDATE를 줄인다.
        List<Map.Entry<Long, Long>> entries = touched.stream()
                .map(accountId -> Map.entry(accountId, balances.get(accountId)))
                .toList();
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

    private void reserveAccountNumberRanges(Phase2BulkSeedSpec spec) {
        // 계좌 INSERT와 함께 최종 사용 번호까지 예약해 중간 실패 후 앱 채번 충돌을 막는다.
        int shortDepositCount = spec.maturedCount() + spec.nearMaturityCount();
        reserveAccountNumber("DEMAND_DEPOSIT", null, "10", 6_000_000 + spec.customerCount());
        reserveAccountNumber(
                "TIME_DEPOSIT",
                product("PRD_BASIC_DEP").id(),
                "20",
                6_000_000 + spec.customerCount() - shortDepositCount);
        reserveAccountNumber("TIME_DEPOSIT", product("PRD_SHORT_DEP").id(), "23", 6_000_000 + shortDepositCount);
        reserveAccountNumber(
                "INSTALLMENT_SAVINGS", product("PRD_REGULAR_SAVE").id(), "32", 6_000_000 + spec.customerCount());
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
            int shortDepositCount = spec.maturedCount() + spec.nearMaturityCount();
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
                    "UPDATE account_number_sequence SET last_sequence=GREATEST(last_sequence, ?) WHERE bank_code='088' AND product_prefix IN ('10','32')",
                    6_000_000 + spec.customerCount());
            jdbc.update(
                    "UPDATE account_number_sequence SET last_sequence=GREATEST(last_sequence, ?) WHERE bank_code='088' AND product_prefix='20'",
                    6_000_000 + spec.customerCount() - shortDepositCount);
            jdbc.update(
                    "UPDATE account_number_sequence SET last_sequence=GREATEST(last_sequence, ?) WHERE bank_code='088' AND product_prefix='23'",
                    6_000_000 + shortDepositCount);
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
                    "SELECT product_id, product_code FROM product WHERE product_code=?",
                    (result, row) -> new ProductSeed(result.getLong(1), result.getString(2)),
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

    record ProductSeed(long id, String code) {}

    private record VoucherKey(LocalDate date, String type) {}

    record RangeProgress(long minimum, long maximum, int count) {}

    private record VoucherSeed(
            String number, LocalDate tradeDate, String type, String description, LocalDateTime createdAt) {}

    private record LockedAccounts(Map<Long, Long> balances, Map<Long, String> accountNumbers) {}

    private record TransferSeed(
            int index,
            long withdrawalAccountId,
            long depositAccountId,
            String depositAccountNumber,
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
