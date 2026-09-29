package com.shinhan.corebank.common.init;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Phase2MinimumSeedService {

    private static final int ACCOUNTS_PER_CUSTOMER = 3;
    private static final int BATCH_SIZE = 1_000;
    private static final long DEMAND_INITIAL_BALANCE = 100_000_000L;
    private static final long DEPOSIT_INITIAL_BALANCE = 5_000_000L;
    private static final long SAVINGS_INITIAL_BALANCE = 1_000_000L;
    private static final String PASSWORD_HASH = "$2y$10$1NOtaTsHuD0rdffA3ReFKO5S0J4bHlVES6okQMYubUd0OuVFfMZXa";
    private static final String OPENING_VOUCHER_NUMBER = "20260901-OPN-600001";

    private final JdbcTemplate jdbc;

    public Phase2MinimumSeedService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Phase2MinimumSeedReport seed(Phase2MinimumSeedSpec spec) {
        // 고정 생성 규칙으로 적재한 뒤 같은 트랜잭션에서 건수와 원장 정합성을 검증한다.
        long startedAt = System.nanoTime();
        long[] finalBalances = calculateFinalBalances(spec);

        insertCustomers(spec);
        insertTransferLimits(spec);
        insertAccounts(spec, finalBalances);
        insertInitialLedgerEntries(spec);
        insertTransfersAndLedgerPairs(spec);
        insertAutoTransfers(spec);
        advanceLedgerSequence(spec);
        insertOpeningVoucher(spec, finalBalances);
        validate(spec);

        return new Phase2MinimumSeedReport(
                spec.customerCount(),
                spec.accountCount(),
                spec.ledgerEntryCount(),
                spec.autoTransferCount(),
                spec.transferCount(),
                Duration.ofNanos(System.nanoTime() - startedAt));
    }

    private long[] calculateFinalBalances(Phase2MinimumSeedSpec spec) {
        long[] balances = initialBalances(spec);
        for (int transferIndex = 0; transferIndex < spec.transferCount(); transferIndex++) {
            int withdrawal = demandAccountIndex(transferIndex % spec.customerCount());
            int deposit = demandAccountIndex(depositCustomerIndex(transferIndex, spec.customerCount()));
            long amount = transferAmount(transferIndex);
            balances[withdrawal] -= amount;
            balances[deposit] += amount;
        }
        for (long balance : balances) {
            if (balance < 0) {
                throw new IllegalStateException("PH-60 생성 결과에 음수 잔액이 있습니다.");
            }
        }
        return balances;
    }

    private long[] initialBalances(Phase2MinimumSeedSpec spec) {
        long[] balances = new long[spec.accountCount()];
        for (int index = 0; index < balances.length; index++) {
            balances[index] = switch (index % ACCOUNTS_PER_CUSTOMER) {
                case 0 -> DEMAND_INITIAL_BALANCE;
                case 1 -> DEPOSIT_INITIAL_BALANCE;
                default -> SAVINGS_INITIAL_BALANCE;
            };
        }
        return balances;
    }

    private void insertCustomers(Phase2MinimumSeedSpec spec) {
        String sql =
                """
                INSERT IGNORE INTO customer (
                    customer_id, user_id, password_hash, user_name, birth_date, email, phone_number,
                    login_failure_count, account_locked, password_changed_at,
                    joined_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 0, FALSE, ?, ?, ?, ?)
                """;
        batch(spec.customerCount(), sql, (statement, index) -> {
            long sequence = index + 1L;
            LocalDateTime timestamp = spec.baseDateTime().minusDays(30).plusSeconds(index);
            statement.setLong(1, spec.customerIdStart() + index);
            statement.setString(2, String.format(Locale.ROOT, "ph60_user_%05d", sequence));
            statement.setString(3, PASSWORD_HASH);
            statement.setString(4, String.format(Locale.ROOT, "PH60고객%05d", sequence));
            statement.setDate(5, Date.valueOf(LocalDate.of(1970 + index % 30, index % 12 + 1, index % 28 + 1)));
            statement.setString(6, String.format(Locale.ROOT, "ph60_%05d@example.com", sequence));
            statement.setString(7, String.format(Locale.ROOT, "010%08d", sequence));
            setTimestamp(statement, 8, timestamp);
            setTimestamp(statement, 9, timestamp);
            setTimestamp(statement, 10, timestamp);
            setTimestamp(statement, 11, timestamp);
        });
    }

    private void insertTransferLimits(Phase2MinimumSeedSpec spec) {
        String sql =
                """
                INSERT IGNORE INTO transfer_limit
                    (customer_id, one_time_limit, daily_limit, created_at, updated_at)
                VALUES (?, 10000000, 50000000, ?, ?)
                """;
        batch(spec.customerCount(), sql, (statement, index) -> {
            statement.setLong(1, spec.customerIdStart() + index);
            setTimestamp(statement, 2, spec.baseDateTime());
            setTimestamp(statement, 3, spec.baseDateTime());
        });
    }

    private void insertAccounts(Phase2MinimumSeedSpec spec, long[] finalBalances) {
        Long depositProductId = productId("PRD_BASIC_DEP");
        Long savingsProductId = productId("PRD_REGULAR_SAVE");
        String sql =
                """
                INSERT IGNORE INTO account (
                    account_id, account_number, customer_id, product_id, account_type, balance, status,
                    password_hash, password_failure_count, password_locked, alias, display_order,
                    withdrawal_registered, withdrawal_registered_at, opened_date, maturity_date,
                    closed_date, last_transaction_at, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, 0, FALSE, ?, ?, ?, ?, ?, ?, NULL, ?, 0, ?, ?)
                """;
        batch(spec.accountCount(), sql, (statement, index) -> {
            int customerIndex = index / ACCOUNTS_PER_CUSTOMER;
            int accountKind = index % ACCOUNTS_PER_CUSTOMER;
            LocalDateTime openedAt = spec.baseDateTime().minusYears(1).plusDays(customerIndex % 180);
            LocalDate maturityDate = maturityDate(spec, customerIndex, accountKind);
            statement.setLong(1, spec.accountIdStart() + index);
            statement.setString(2, accountNumber(spec, index));
            statement.setLong(3, spec.customerIdStart() + customerIndex);
            if (accountKind == 0) {
                statement.setNull(4, java.sql.Types.BIGINT);
            } else {
                statement.setLong(4, accountKind == 1 ? depositProductId : savingsProductId);
            }
            statement.setString(5, accountType(accountKind));
            statement.setLong(6, finalBalances[index]);
            statement.setString(7, PASSWORD_HASH);
            statement.setString(8, accountKind == 0 ? "PH60 주거래" : null);
            statement.setInt(9, accountKind + 1);
            statement.setBoolean(10, accountKind == 0);
            if (accountKind == 0) {
                setTimestamp(statement, 11, openedAt.plusDays(1));
            } else {
                statement.setNull(11, java.sql.Types.TIMESTAMP);
            }
            setTimestamp(statement, 12, openedAt);
            if (maturityDate == null) {
                statement.setNull(13, java.sql.Types.DATE);
            } else {
                statement.setDate(13, Date.valueOf(maturityDate));
            }
            setTimestamp(statement, 14, spec.baseDateTime().plusSeconds(spec.transferCount()));
            setTimestamp(statement, 15, openedAt);
            setTimestamp(statement, 16, spec.baseDateTime());
        });
    }

    private void insertInitialLedgerEntries(Phase2MinimumSeedSpec spec) {
        String sql =
                """
                INSERT IGNORE INTO ledger_entry (
                    ledger_entry_id, occurred_at, account_id, transfer_id, transaction_number,
                    direction, amount, balance_after, transaction_type, transaction_content,
                    channel, reversed, reversal_id
                ) VALUES (?, ?, ?, NULL, ?, 'DEPOSIT', ?, ?, 'PH60_SEED_INITIAL', 'PH60개시', 'BT', FALSE, NULL)
                """;
        batch(spec.accountCount(), sql, (statement, index) -> {
            long initialBalance = initialBalance(index);
            statement.setLong(1, spec.ledgerEntryIdStart() + index);
            setTimestamp(statement, 2, spec.baseDateTime().minusSeconds(1));
            statement.setLong(3, spec.accountIdStart() + index);
            statement.setString(4, transactionNumber(spec, index));
            statement.setLong(5, initialBalance);
            statement.setLong(6, initialBalance);
        });
    }

    private void insertTransfersAndLedgerPairs(Phase2MinimumSeedSpec spec) {
        long[] runningBalances = initialBalances(spec);
        for (int offset = 0; offset < spec.transferCount(); offset += BATCH_SIZE) {
            int size = Math.min(BATCH_SIZE, spec.transferCount() - offset);
            insertTransferBatch(spec, runningBalances, offset, size);
        }
    }

    private void insertTransferBatch(Phase2MinimumSeedSpec spec, long[] runningBalances, int offset, int size) {
        long[] withdrawalAfter = new long[size];
        long[] depositAfter = new long[size];
        for (int localIndex = 0; localIndex < size; localIndex++) {
            int transferIndex = offset + localIndex;
            int withdrawal = demandAccountIndex(transferIndex % spec.customerCount());
            int deposit = demandAccountIndex(depositCustomerIndex(transferIndex, spec.customerCount()));
            long amount = transferAmount(transferIndex);
            runningBalances[withdrawal] -= amount;
            runningBalances[deposit] += amount;
            withdrawalAfter[localIndex] = runningBalances[withdrawal];
            depositAfter[localIndex] = runningBalances[deposit];
        }

        String transferSql =
                """
                INSERT IGNORE INTO transfer (
                    transfer_id, transaction_number, withdrawal_account_id, deposit_account_id,
                    deposit_account_number, payee_name, amount, fee, transfer_type, channel, status,
                    source_type, source_id, execution_date, my_passbook_memo, recipient_passbook_memo,
                    withdrawal_balance_after, error_code, error_message, transferred_at, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 0, 'IMMEDIATE', 'BT', 'SUCCESS',
                          NULL, NULL, NULL, 'PH60', 'PH60', ?, NULL, NULL, ?, ?)
                """;
        jdbc.batchUpdate(transferSql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement statement, int localIndex) throws SQLException {
                int transferIndex = offset + localIndex;
                int withdrawalCustomer = transferIndex % spec.customerCount();
                int depositCustomer = depositCustomerIndex(transferIndex, spec.customerCount());
                long occurredSequence = spec.accountCount() + (long) transferIndex;
                LocalDateTime occurredAt = spec.baseDateTime().plusSeconds(transferIndex);
                statement.setLong(1, spec.transferIdStart() + transferIndex);
                statement.setString(2, transactionNumber(spec, occurredSequence));
                statement.setLong(3, spec.accountIdStart() + demandAccountIndex(withdrawalCustomer));
                statement.setLong(4, spec.accountIdStart() + demandAccountIndex(depositCustomer));
                statement.setString(5, accountNumber(spec, demandAccountIndex(depositCustomer)));
                statement.setString(6, String.format(Locale.ROOT, "PH60고객%05d", depositCustomer + 1));
                statement.setLong(7, transferAmount(transferIndex));
                statement.setLong(8, withdrawalAfter[localIndex]);
                setTimestamp(statement, 9, occurredAt);
                setTimestamp(statement, 10, occurredAt);
            }

            @Override
            public int getBatchSize() {
                return size;
            }
        });

        String ledgerSql =
                """
                INSERT IGNORE INTO ledger_entry (
                    ledger_entry_id, occurred_at, account_id, transfer_id, transaction_number,
                    direction, amount, balance_after, transaction_type, transaction_content,
                    channel, reversed, reversal_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PH60_SEED_TRANSFER', 'PH60이체', 'BT', FALSE, NULL)
                """;
        jdbc.batchUpdate(ledgerSql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement statement, int rowIndex) throws SQLException {
                int localIndex = rowIndex / 2;
                boolean withdrawal = rowIndex % 2 == 0;
                int transferIndex = offset + localIndex;
                int customerIndex = withdrawal
                        ? transferIndex % spec.customerCount()
                        : depositCustomerIndex(transferIndex, spec.customerCount());
                long ledgerOffset = spec.accountCount() + (long) transferIndex * 2 + (withdrawal ? 0 : 1);
                LocalDateTime occurredAt = spec.baseDateTime().plusSeconds(transferIndex);
                statement.setLong(1, spec.ledgerEntryIdStart() + ledgerOffset);
                setTimestamp(statement, 2, occurredAt);
                statement.setLong(3, spec.accountIdStart() + demandAccountIndex(customerIndex));
                statement.setLong(4, spec.transferIdStart() + transferIndex);
                statement.setString(5, transactionNumber(spec, spec.accountCount() + (long) transferIndex));
                statement.setString(6, withdrawal ? "WITHDRAWAL" : "DEPOSIT");
                statement.setLong(7, transferAmount(transferIndex));
                statement.setLong(8, withdrawal ? withdrawalAfter[localIndex] : depositAfter[localIndex]);
            }

            @Override
            public int getBatchSize() {
                return size * 2;
            }
        });
    }

    private void insertAutoTransfers(Phase2MinimumSeedSpec spec) {
        String sql =
                """
                INSERT IGNORE INTO auto_transfer (
                    auto_transfer_id, customer_id, withdrawal_account_id, deposit_account_number,
                    payee_name, amount, cycle_months, transfer_day, start_date, end_date,
                    next_execution_date, my_passbook_memo, recipient_passbook_memo, status,
                    registered_at, terminated_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PH60', 'PH60', 'NORMAL', ?, NULL, ?, 0)
                """;
        batch(spec.autoTransferCount(), sql, (statement, index) -> {
            int depositCustomer = (index + 1) % spec.customerCount();
            int transferDay = index % 28 + 1;
            LocalDate startDate = spec.baseDateTime().toLocalDate();
            statement.setLong(1, spec.autoTransferIdStart() + index);
            statement.setLong(2, spec.customerIdStart() + index);
            statement.setLong(3, spec.accountIdStart() + demandAccountIndex(index));
            statement.setString(4, accountNumber(spec, demandAccountIndex(depositCustomer)));
            statement.setString(5, String.format(Locale.ROOT, "PH60고객%05d", depositCustomer + 1));
            statement.setLong(6, 10_000L + index % 100 * 1_000L);
            statement.setInt(
                    7,
                    switch (index % 3) {
                        case 0 -> 1;
                        case 1 -> 3;
                        default -> 6;
                    });
            statement.setInt(8, transferDay);
            statement.setDate(9, Date.valueOf(startDate));
            statement.setDate(10, Date.valueOf(startDate.plusYears(1)));
            statement.setDate(11, Date.valueOf(startDate.plusMonths(1).withDayOfMonth(transferDay)));
            setTimestamp(statement, 12, spec.baseDateTime());
            setTimestamp(statement, 13, spec.baseDateTime());
        });
    }

    private void advanceLedgerSequence(Phase2MinimumSeedSpec spec) {
        jdbc.update(
                "INSERT IGNORE INTO ledger_entry_id_sequence (sequence_id) VALUES (?)",
                spec.ledgerEntryIdStart() + spec.ledgerEntryCount() - 1);
    }

    private void insertOpeningVoucher(Phase2MinimumSeedSpec spec, long[] finalBalances) {
        long totalBalance = 0;
        for (long balance : finalBalances) {
            totalBalance = Math.addExact(totalBalance, balance);
        }
        LocalDate tradeDate = spec.baseDateTime().toLocalDate();
        jdbc.update(
                """
                INSERT IGNORE INTO gl_voucher (voucher_no, trade_date, tx_type, description, created_at)
                VALUES (?, ?, 'OPENING', 'PH-60 최소 시드 개시 잔액', ?)
                """,
                OPENING_VOUCHER_NUMBER,
                Date.valueOf(tradeDate),
                Timestamp.valueOf(spec.baseDateTime()));
        jdbc.update(
                """
                INSERT IGNORE INTO gl_journal_entry
                    (voucher_no, line_no, account_code, dr_cr, amount, trade_date, created_at)
                VALUES (?, 1, '10100', 'DEBIT', ?, ?, ?),
                       (?, 2, '20100', 'CREDIT', ?, ?, ?)
                """,
                OPENING_VOUCHER_NUMBER,
                totalBalance,
                Date.valueOf(tradeDate),
                Timestamp.valueOf(spec.baseDateTime()),
                OPENING_VOUCHER_NUMBER,
                totalBalance,
                Date.valueOf(tradeDate),
                Timestamp.valueOf(spec.baseDateTime()));
    }

    private void validate(Phase2MinimumSeedSpec spec) {
        requireCount(
                "customers",
                spec.customerCount(),
                "SELECT COUNT(*) FROM customer WHERE customer_id BETWEEN ? AND ?",
                spec.customerIdStart(),
                spec.customerIdStart() + spec.customerCount() - 1);
        requireCount(
                "accounts",
                spec.accountCount(),
                "SELECT COUNT(*) FROM account WHERE account_id BETWEEN ? AND ? AND account_number LIKE ?",
                spec.accountIdStart(),
                spec.accountIdStart() + spec.accountCount() - 1,
                spec.accountBankCode() + "%");
        requireCount(
                "ledger entries",
                spec.ledgerEntryCount(),
                "SELECT COUNT(*) FROM ledger_entry WHERE ledger_entry_id BETWEEN ? AND ?",
                spec.ledgerEntryIdStart(),
                spec.ledgerEntryIdStart() + spec.ledgerEntryCount() - 1);
        requireCount(
                "auto transfers",
                spec.autoTransferCount(),
                "SELECT COUNT(*) FROM auto_transfer WHERE auto_transfer_id BETWEEN ? AND ?",
                spec.autoTransferIdStart(),
                spec.autoTransferIdStart() + spec.autoTransferCount() - 1);
        requireCount(
                "transfers",
                spec.transferCount(),
                "SELECT COUNT(*) FROM transfer WHERE transfer_id BETWEEN ? AND ? AND status = 'SUCCESS'",
                spec.transferIdStart(),
                spec.transferIdStart() + spec.transferCount() - 1);
        requireCount(
                "near maturity accounts",
                spec.nearMaturityAccountCount(),
                """
                SELECT COUNT(*) FROM account
                WHERE account_id BETWEEN ? AND ?
                  AND maturity_date > ? AND maturity_date <= ?
                """,
                spec.accountIdStart(),
                spec.accountIdStart() + spec.accountCount() - 1,
                Date.valueOf(spec.baseDateTime().toLocalDate()),
                Date.valueOf(spec.baseDateTime().toLocalDate().plusDays(30)));
        requireCount(
                "matured accounts",
                0,
                "SELECT COUNT(*) FROM account WHERE account_id BETWEEN ? AND ? AND status = 'MATURED'",
                spec.accountIdStart(),
                spec.accountIdStart() + spec.accountCount() - 1);

        int unpaired = count(
                """
                SELECT COUNT(*) FROM (
                    SELECT t.transfer_id
                    FROM transfer t
                    JOIN ledger_entry le ON le.transfer_id = t.transfer_id
                    WHERE t.transfer_id BETWEEN ? AND ? AND t.status = 'SUCCESS'
                    GROUP BY t.transfer_id
                    HAVING COUNT(*) <> 2
                       OR SUM(le.direction = 'WITHDRAWAL') <> 1
                       OR SUM(le.direction = 'DEPOSIT') <> 1
                ) broken
                """,
                spec.transferIdStart(),
                spec.transferIdStart() + spec.transferCount() - 1);
        if (unpaired != 0) {
            throw new IllegalStateException("PH-60 원장 쌍 정합성 검증에 실패했습니다: " + unpaired);
        }

        int balanceMismatches = count(
                """
                SELECT COUNT(*) FROM (
                    SELECT a.account_id
                    FROM account a
                    JOIN ledger_entry le ON le.account_id = a.account_id
                    WHERE a.account_id BETWEEN ? AND ?
                    GROUP BY a.account_id, a.balance
                    HAVING a.balance <> SUM(CASE WHEN le.direction = 'DEPOSIT' THEN le.amount ELSE -le.amount END)
                ) broken
                """,
                spec.accountIdStart(),
                spec.accountIdStart() + spec.accountCount() - 1);
        if (balanceMismatches != 0) {
            throw new IllegalStateException("PH-60 계좌 잔액 정합성 검증에 실패했습니다: " + balanceMismatches);
        }

        int unbalancedVoucher = count(
                """
                SELECT COUNT(*) FROM (
                    SELECT voucher_no
                    FROM gl_journal_entry
                    WHERE voucher_no = ?
                    GROUP BY voucher_no
                    HAVING SUM(CASE WHEN dr_cr = 'DEBIT' THEN amount ELSE 0 END)
                         <> SUM(CASE WHEN dr_cr = 'CREDIT' THEN amount ELSE 0 END)
                ) broken
                """,
                OPENING_VOUCHER_NUMBER);
        if (unbalancedVoucher != 0) {
            throw new IllegalStateException("PH-60 개시 잔액 전표의 차대변이 일치하지 않습니다.");
        }
    }

    private void requireCount(String label, int expected, String sql, Object... args) {
        int actual = count(sql, args);
        if (actual != expected) {
            throw new IllegalStateException(
                    "PH-60 " + label + " count mismatch: expected=" + expected + ", actual=" + actual);
        }
    }

    private int count(String sql, Object... args) {
        Integer result = jdbc.queryForObject(sql, Integer.class, args);
        return result == null ? 0 : result;
    }

    private Long productId(String productCode) {
        Long result =
                jdbc.queryForObject("SELECT product_id FROM product WHERE product_code = ?", Long.class, productCode);
        if (result == null) {
            throw new IllegalStateException("PH-60 필수 상품이 없습니다: " + productCode);
        }
        return result;
    }

    private void batch(int total, String sql, BatchBinder binder) {
        for (int offset = 0; offset < total; offset += BATCH_SIZE) {
            int batchOffset = offset;
            int size = Math.min(BATCH_SIZE, total - offset);
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

    private int depositCustomerIndex(int transferIndex, int customerCount) {
        int withdrawal = transferIndex % customerCount;
        int deposit = Math.floorMod(transferIndex * 37 + 1, customerCount);
        return deposit == withdrawal ? (deposit + 1) % customerCount : deposit;
    }

    private int demandAccountIndex(int customerIndex) {
        return customerIndex * ACCOUNTS_PER_CUSTOMER;
    }

    private long transferAmount(int transferIndex) {
        return 10_000L + transferIndex % 90 * 1_000L;
    }

    private long initialBalance(int accountIndex) {
        return switch (accountIndex % ACCOUNTS_PER_CUSTOMER) {
            case 0 -> DEMAND_INITIAL_BALANCE;
            case 1 -> DEPOSIT_INITIAL_BALANCE;
            default -> SAVINGS_INITIAL_BALANCE;
        };
    }

    private String accountNumber(Phase2MinimumSeedSpec spec, int accountIndex) {
        String productPrefix =
                switch (accountIndex % ACCOUNTS_PER_CUSTOMER) {
                    case 0 -> "10";
                    case 1 -> "20";
                    default -> "30";
                };
        int sequence = accountIndex / ACCOUNTS_PER_CUSTOMER + 1;
        return spec.accountBankCode() + productPrefix + String.format(Locale.ROOT, "%07d", sequence);
    }

    private String accountType(int accountKind) {
        return switch (accountKind) {
            case 0 -> "DEMAND_DEPOSIT";
            case 1 -> "TIME_DEPOSIT";
            default -> "INSTALLMENT_SAVINGS";
        };
    }

    private LocalDate maturityDate(Phase2MinimumSeedSpec spec, int customerIndex, int accountKind) {
        if (accountKind == 0) {
            return null;
        }
        if (accountKind == 1 && customerIndex < spec.nearMaturityAccountCount()) {
            return spec.baseDateTime().toLocalDate().plusDays(customerIndex % 30 + 1L);
        }
        return spec.baseDateTime().toLocalDate().plusMonths(accountKind == 1 ? 12 : 24);
    }

    private String transactionNumber(Phase2MinimumSeedSpec spec, long offset) {
        long sequence = spec.transactionSequenceStart() + offset;
        return spec.baseDateTime().toLocalDate().toString().replace("-", "")
                + "BT"
                + String.format(Locale.ROOT, "%010d", sequence);
    }

    private void setTimestamp(PreparedStatement statement, int index, LocalDateTime value) throws SQLException {
        statement.setTimestamp(index, Timestamp.valueOf(value));
    }

    @FunctionalInterface
    private interface BatchBinder {
        void bind(PreparedStatement statement, int index) throws SQLException;
    }
}
