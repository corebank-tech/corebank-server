package com.shinhan.infoetl.adapter.out.jdbc;

import com.shinhan.infoetl.application.port.out.EtlStorageException;
import com.shinhan.infoetl.application.port.out.ReplicaBalancePort;
import com.shinhan.infoetl.domain.BalanceRow;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class JdbcReplicaBalanceAdapter implements ReplicaBalancePort {

    // 거래일이 NULL이면 발생일을 영업일로 올린다(결정 3). 주말이거나 holiday에 있는 날은 건너뛰며 연휴는 31일을 넘지 않는다고 본다.
    private static String tradeDate(String alias) {
        String occurredDate = "DATE(" + alias + ".occurred_at)";
        return "COALESCE(" + alias + ".trade_date, (SELECT MIN(" + occurredDate + " + INTERVAL o.n DAY) FROM offsets o"
                + " WHERE WEEKDAY(" + occurredDate + " + INTERVAL o.n DAY) < 5"
                + " AND " + occurredDate + " + INTERVAL o.n DAY NOT IN (SELECT holiday_date FROM holiday)))";
    }

    // P3 설계 §3-3 잔액 쿼리. ? 세 개는 모두 기준일 D다. 해지일(close_td)은 7일 창 없이 원장 끝까지 본다.
    private static final String SQL =
            """
            WITH RECURSIVE offsets(n) AS (
              SELECT 0 UNION ALL SELECT n + 1 FROM offsets WHERE n < 30
            ), le AS (
              SELECT x.account_id, x.balance_after, x.occurred_at, x.ledger_entry_id,
                     %s AS td
              FROM ledger_entry x
              WHERE x.occurred_at < ? + INTERVAL 7 DAY
            ), last_entry AS (
              SELECT account_id, balance_after,
                     ROW_NUMBER() OVER (PARTITION BY account_id
                                        ORDER BY occurred_at DESC, ledger_entry_id DESC) AS rn
              FROM le WHERE td <= ?
            ), close_td AS (
              SELECT l.account_id, MAX(%s) AS close_date
              FROM ledger_entry l JOIN account a ON a.account_id = l.account_id AND a.status = 'CLOSED'
              GROUP BY l.account_id
            )
            SELECT a.account_id, a.customer_id, a.account_type, a.product_id, p.product_group, l.balance_after
            FROM last_entry l
            JOIN account a ON a.account_id = l.account_id
            LEFT JOIN product p ON p.product_id = a.product_id
            LEFT JOIN close_td c ON c.account_id = a.account_id
            WHERE l.rn = 1
              AND (c.close_date IS NULL OR c.close_date >= ?)
            """
                    .formatted(tradeDate("x"), tradeDate("l"));

    private final JdbcConnectionFactory connections;

    public JdbcReplicaBalanceAdapter(JdbcConnectionFactory connections) {
        this.connections = connections;
    }

    @Override
    public List<BalanceRow> readBalances(LocalDate baseDate) {
        try (Connection replica = connections.open()) {
            replica.setReadOnly(true);
            return query(replica, baseDate);
        } catch (SQLException e) {
            throw new EtlStorageException("레플리카 잔액 읽기에 실패했습니다: " + e.getMessage(), e);
        }
    }

    private List<BalanceRow> query(Connection replica, LocalDate baseDate) throws SQLException {
        List<BalanceRow> rows = new ArrayList<>();
        try (PreparedStatement statement = replica.prepareStatement(SQL)) {
            statement.setObject(1, baseDate);
            statement.setObject(2, baseDate);
            statement.setObject(3, baseDate);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    long productId = resultSet.getLong(4);
                    Long nullableProductId = resultSet.wasNull() ? null : productId;
                    rows.add(new BalanceRow(
                            resultSet.getLong(1),
                            resultSet.getLong(2),
                            resultSet.getString(3),
                            nullableProductId,
                            resultSet.getString(5),
                            resultSet.getLong(6)));
                }
            }
        }
        return rows;
    }
}
