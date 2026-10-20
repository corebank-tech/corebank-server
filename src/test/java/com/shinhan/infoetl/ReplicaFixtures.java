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
                PreparedStatement statement =
                        connection.prepareStatement("INSERT INTO product (product_id, product_group) VALUES (?, ?)")) {
            statement.setLong(1, productId);
            statement.setString(2, group);
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
}
