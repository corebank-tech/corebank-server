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

    // 가입 건에서 시작해 원장 입금 행의 거래일이 D인 것을 찾는다. 원장 날짜 창을 쓰지 않아 창 밖 오류가 없다.
    private static final String SUBSCRIBE_SQL =
            """
            SELECT ps.account_id, ps.customer_id, ps.product_id, p.product_code, p.product_name, p.product_group
            FROM product_subscription ps
            JOIN product p ON p.product_id = ps.product_id
            JOIN ledger_entry l ON l.transaction_number = ps.transaction_number AND l.account_id = ps.account_id
            WHERE ps.status = 'SUCCESS'
              AND l.transaction_type = 'PRODUCT_SUBSCRIPTION'
              AND l.direction = 'DEPOSIT'
              AND COALESCE(l.trade_date, DATE(l.occurred_at)) = ?
            ORDER BY ps.account_id
            """;

    // 해지된 상품 계좌의 전체 원장 마지막 거래일이 D인 것. 7일 창을 쓰지 않는다(BalanceReader와 같은 이유).
    private static final String CLOSE_SQL =
            """
            SELECT a.account_id, a.customer_id, a.product_id, p.product_code, p.product_name, p.product_group
            FROM account a
            JOIN product p ON p.product_id = a.product_id
            JOIN (SELECT l.account_id, MAX(COALESCE(l.trade_date, DATE(l.occurred_at))) AS last_td
                  FROM ledger_entry l JOIN account c ON c.account_id = l.account_id
                  WHERE c.status = 'CLOSED' AND c.product_id IS NOT NULL
                  GROUP BY l.account_id) x ON x.account_id = a.account_id
            WHERE a.status = 'CLOSED' AND x.last_td = ?
            ORDER BY a.account_id
            """;

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
            return rows;
        } catch (SQLException e) {
            throw new EtlStorageException("레플리카 이벤트 읽기에 실패했습니다: " + e.getMessage(), e);
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
