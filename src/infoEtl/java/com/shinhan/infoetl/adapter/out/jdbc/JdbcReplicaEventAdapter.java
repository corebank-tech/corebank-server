package com.shinhan.infoetl.adapter.out.jdbc;

import com.shinhan.infoetl.application.port.out.EtlStorageException;
import com.shinhan.infoetl.application.port.out.ReplicaEventPort;
import com.shinhan.infoetl.domain.EventType;
import com.shinhan.infoetl.domain.SubscriptionEventRow;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class JdbcReplicaEventAdapter implements ReplicaEventPort {

    // 가입 트랜잭션이 저장한 논리 영업일이 D인 성공 가입. 초입금 원장이 없는 정기적금도 잡힌다(설계 §3-3).
    private static final String SUBSCRIBE_SQL =
            """
            SELECT ps.account_id, ps.customer_id, ps.product_id, p.product_code, p.product_name, p.product_group
            FROM product_subscription ps
            JOIN product p ON p.product_id = ps.product_id
            WHERE ps.status = 'SUCCESS'
              AND ps.subscription_trade_date = ?
            ORDER BY ps.account_id
            """;

    // 해지 트랜잭션이 저장한 논리 영업일이 D인 상품 계좌. 원장이 없는 해지도 포함한다.
    private static final String CLOSE_SQL =
            """
            SELECT a.account_id, a.customer_id, a.product_id, p.product_code, p.product_name, p.product_group
            FROM account a
            JOIN product p ON p.product_id = a.product_id
            WHERE a.status = 'CLOSED'
              AND a.product_id IS NOT NULL
              AND a.closed_trade_date = ?
            ORDER BY a.account_id
            """;

    // 원장이 있는 해지 계좌의 마지막 거래일이 closed_trade_date와 다르면 원천 계약 위반이다.
    private static final String CLOSE_MISMATCH_SQL =
            """
            WITH RECURSIVE %s
            SELECT a.account_id, a.closed_trade_date, x.last_ledger_date
            FROM account a
            JOIN (SELECT l.account_id, MAX(%s) AS last_ledger_date
                  FROM ledger_entry l JOIN account c ON c.account_id = l.account_id
                  WHERE c.status = 'CLOSED' AND c.product_id IS NOT NULL AND c.closed_trade_date = ?
                  GROUP BY l.account_id) x ON x.account_id = a.account_id
            WHERE x.last_ledger_date <> a.closed_trade_date
            ORDER BY a.account_id
            LIMIT 5
            """
                    .formatted(TradeDateSql.OFFSETS, TradeDateSql.of("l"));

    private final JdbcConnectionFactory connections;

    public JdbcReplicaEventAdapter(JdbcConnectionFactory connections) {
        this.connections = connections;
    }

    @Override
    public List<SubscriptionEventRow> readSubscriptionEvents(LocalDate baseDate) {
        try (Connection replica = connections.open()) {
            replica.setReadOnly(true);
            List<SubscriptionEventRow> rows = new ArrayList<>();
            rows.addAll(query(replica, SUBSCRIBE_SQL, EventType.SUBSCRIBE, baseDate));
            rows.addAll(query(replica, CLOSE_SQL, EventType.CLOSE, baseDate));
            requireClosedTradeDateMatchesLedger(replica, baseDate);
            return rows;
        } catch (SQLException e) {
            throw new EtlStorageException("레플리카 이벤트 읽기에 실패했습니다: " + e.getMessage(), e);
        }
    }

    private void requireClosedTradeDateMatchesLedger(Connection replica, LocalDate baseDate) throws SQLException {
        List<String> mismatches = new ArrayList<>();
        try (PreparedStatement statement = replica.prepareStatement(CLOSE_MISMATCH_SQL)) {
            statement.setObject(1, baseDate);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    mismatches.add("accountId=" + resultSet.getLong(1) + ", closedTradeDate="
                            + resultSet.getObject(2, LocalDate.class) + ", lastLedgerDate="
                            + resultSet.getObject(3, LocalDate.class));
                }
            }
        }
        if (!mismatches.isEmpty()) {
            throw new IllegalStateException("해지 거래일이 원장의 마지막 거래일과 다릅니다(원천 계약 위반): " + mismatches);
        }
    }

    private List<SubscriptionEventRow> query(Connection replica, String sql, EventType type, LocalDate baseDate)
            throws SQLException {
        List<SubscriptionEventRow> rows = new ArrayList<>();
        try (PreparedStatement statement = replica.prepareStatement(sql)) {
            statement.setObject(1, baseDate);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new SubscriptionEventRow(
                            type,
                            resultSet.getLong(1),
                            resultSet.getLong(2),
                            resultSet.getLong(3),
                            resultSet.getString(4),
                            resultSet.getString(5),
                            resultSet.getString(6)));
                }
            }
        }
        return rows;
    }
}
