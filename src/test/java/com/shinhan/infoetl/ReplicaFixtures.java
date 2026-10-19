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
    }
    // 가입 초입금 원장 기표(상품 계좌 쪽 입금 행)를 거래번호와 함께 넣는다.
    public static void insertSubscriptionDepositLedger(
            long ledgerEntryId, long accountId, String transactionNumber, LocalDateTime occurredAt, LocalDate tradeDate)
            throws SQLException {
        insertLedgerDetailed(
                ledgerEntryId,
                accountId,
                transactionNumber,
                "DEPOSIT",
                1_000L,
                "PRODUCT_SUBSCRIPTION",
                occurredAt,
                tradeDate);
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
    }

    public static void insertSubscription(
            long subscriptionId,
            long customerId,
            long productId,
            Long accountId,
            String status,
            String transactionNumber)
            throws SQLException {
        try (Connection connection = InfoEtlTestDatabase.replica();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO product_subscription (subscription_id, customer_id, product_id, account_id,"
                                + " status, transaction_number) VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setLong(1, subscriptionId);
            statement.setLong(2, customerId);
            statement.setLong(3, productId);
            statement.setObject(4, accountId);
            statement.setString(5, status);
            statement.setString(6, transactionNumber);
            statement.executeUpdate();
        }
    }
}
