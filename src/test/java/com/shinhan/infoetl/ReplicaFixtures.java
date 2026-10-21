package com.shinhan.infoetl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;

// 레플리카 테스트 DB에 계좌·원장 행을 넣는 도구.
public final class ReplicaFixtures {

    private ReplicaFixtures() {}

    public static void insertProduct(long productId, String group) throws SQLException {
        try (Connection connection = InfoEtlTestDatabase.replica();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO product (product_id, product_code, product_name, product_group)"
                                + " VALUES (?, ?, ?, ?)")) {
            statement.setLong(1, productId);
            statement.setString(2, "P" + productId);
            statement.setString(3, "상품" + productId);
            statement.setString(4, group);
            statement.executeUpdate();
        }
    }

    public static void insertHoliday(LocalDate date, String name) throws SQLException {
        try (Connection connection = InfoEtlTestDatabase.replica();
                PreparedStatement statement =
                        connection.prepareStatement("INSERT INTO holiday (holiday_date, holiday_name) VALUES (?, ?)")) {
            statement.setObject(1, date);
            statement.setString(2, name);
            statement.executeUpdate();
        }
    }

    public static void insertAccount(long accountId, long customerId, Long productId, String type, String status)
            throws SQLException {
        try (Connection connection = InfoEtlTestDatabase.replica();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO account (account_id, customer_id, product_id, account_type, status)"
                                + " VALUES (?, ?, ?, ?, ?)")) {
            statement.setLong(1, accountId);
            statement.setLong(2, customerId);
            statement.setObject(3, productId);
            statement.setString(4, type);
            statement.setString(5, status);
            statement.executeUpdate();
        }
    }

    public static void insertLedger(
            long ledgerEntryId, long accountId, long balanceAfter, LocalDateTime occurredAt, LocalDate tradeDate)
            throws SQLException {
        try (Connection connection = InfoEtlTestDatabase.replica();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO ledger_entry (ledger_entry_id, account_id, balance_after, occurred_at,"
                                + " trade_date) VALUES (?, ?, ?, ?, ?)")) {
            statement.setLong(1, ledgerEntryId);
            statement.setLong(2, accountId);
            statement.setLong(3, balanceAfter);
            statement.setObject(4, occurredAt);
            statement.setObject(5, tradeDate);
            statement.executeUpdate();
        }
        recomputeAmounts(accountId);
    }

    // 원장 대조가 맞도록 기표 금액·방향을 계좌의 balance_after 변화에서 다시 계산한다(어떤 순서로 넣어도 일관되게).
    private static void recomputeAmounts(long accountId) throws SQLException {
        try (Connection connection = InfoEtlTestDatabase.replica();
                PreparedStatement statement =
                        connection.prepareStatement("UPDATE ledger_entry l JOIN (SELECT ledger_entry_id, occurred_at,"
                                + " balance_after - LAG(balance_after, 1, 0) OVER (ORDER BY occurred_at, ledger_entry_id)"
                                + " AS delta FROM ledger_entry WHERE account_id = ?) d"
                                + " ON d.ledger_entry_id = l.ledger_entry_id AND d.occurred_at = l.occurred_at"
                                + " SET l.direction = CASE WHEN d.delta >= 0 THEN 'DEPOSIT' ELSE 'WITHDRAWAL' END,"
                                + " l.amount = ABS(d.delta) WHERE l.account_id = ?")) {
            statement.setLong(1, accountId);
            statement.setLong(2, accountId);
            statement.executeUpdate();
        }
    }

    public static void insertGlEntry(String accountCode, String drCr, long amount, LocalDate tradeDate)
            throws SQLException {
        try (Connection connection = InfoEtlTestDatabase.replica();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO gl_journal_entry (account_code, dr_cr, amount, trade_date) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, accountCode);
            statement.setString(2, drCr);
            statement.setLong(3, amount);
            statement.setObject(4, tradeDate);
            statement.executeUpdate();
        }
    }

    public static void insertLedgerDetailed(
            long ledgerEntryId,
            long accountId,
            String transactionNumber,
            String direction,
            long balanceAfter,
            String transactionType,
            LocalDateTime occurredAt,
            LocalDate tradeDate)
            throws SQLException {
        try (Connection connection = InfoEtlTestDatabase.replica();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO ledger_entry (ledger_entry_id, account_id, transaction_number, direction,"
                                + " balance_after, transaction_type, occurred_at, trade_date)"
                                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setLong(1, ledgerEntryId);
            statement.setLong(2, accountId);
            statement.setString(3, transactionNumber);
            statement.setString(4, direction);
            statement.setLong(5, balanceAfter);
            statement.setString(6, transactionType);
            statement.setObject(7, occurredAt);
            statement.setObject(8, tradeDate);
            statement.executeUpdate();
        }
        recomputeAmounts(accountId);
    }

    public static void insertSubscription(
            long subscriptionId,
            long customerId,
            long productId,
            Long accountId,
            String status,
            LocalDate subscriptionTradeDate)
            throws SQLException {
        try (Connection connection = InfoEtlTestDatabase.replica();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO product_subscription (subscription_id, customer_id, product_id, account_id,"
                                + " status, subscription_trade_date) VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setLong(1, subscriptionId);
            statement.setLong(2, customerId);
            statement.setLong(3, productId);
            statement.setObject(4, accountId);
            statement.setString(5, status);
            statement.setObject(6, subscriptionTradeDate);
            statement.executeUpdate();
        }
    }

    // COB 경계처럼 가입 시각의 달력 날짜와 논리 영업일이 다른 가입을 만든다.
    public static void insertSubscriptionAt(
            long subscriptionId,
            long customerId,
            long productId,
            Long accountId,
            LocalDateTime subscribedAt,
            LocalDate subscriptionTradeDate)
            throws SQLException {
        try (Connection connection = InfoEtlTestDatabase.replica();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO product_subscription (subscription_id, customer_id, product_id, account_id,"
                                + " status, subscribed_at, subscription_trade_date) VALUES (?, ?, ?, ?, 'SUCCESS', ?, ?)")) {
            statement.setLong(1, subscriptionId);
            statement.setLong(2, customerId);
            statement.setLong(3, productId);
            statement.setObject(4, accountId);
            statement.setObject(5, subscribedAt);
            statement.setObject(6, subscriptionTradeDate);
            statement.executeUpdate();
        }
    }

    public static void insertClosedAccount(
            long accountId, long customerId, Long productId, String type, LocalDate closedTradeDate)
            throws SQLException {
        try (Connection connection = InfoEtlTestDatabase.replica();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO account (account_id, customer_id, product_id, account_type, status,"
                                + " closed_trade_date) VALUES (?, ?, ?, ?, 'CLOSED', ?)")) {
            statement.setLong(1, accountId);
            statement.setLong(2, customerId);
            statement.setObject(3, productId);
            statement.setString(4, type);
            statement.setObject(5, closedTradeDate);
            statement.executeUpdate();
        }
    }
}
