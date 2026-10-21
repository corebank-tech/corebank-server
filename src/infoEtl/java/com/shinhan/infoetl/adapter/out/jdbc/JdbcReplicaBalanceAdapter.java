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

    // P3 설계 §3-3 잔액 쿼리. ? 두 개는 모두 기준일 D다. 발생일 상한 없이 거래일이 D 이하인 원장을 모두 보고,
    // 해지 계좌는 해지 논리 영업일(closed_trade_date)이 D보다 이전이면 거른다. 원장과의 일치 검사는 이벤트 읽기에서 한다.
    private static final String SQL =
            """
            WITH RECURSIVE %s,
            le AS (
              SELECT x.account_id, x.balance_after, x.occurred_at, x.ledger_entry_id,
                     %s AS td
              FROM ledger_entry x
            ), last_entry AS (
              SELECT account_id, balance_after,
                     ROW_NUMBER() OVER (PARTITION BY account_id
                                        ORDER BY occurred_at DESC, ledger_entry_id DESC) AS rn
              FROM le WHERE td <= ?
            )
            SELECT a.account_id, a.customer_id, a.account_type, a.product_id, p.product_group, l.balance_after
            FROM last_entry l
            JOIN account a ON a.account_id = l.account_id
            LEFT JOIN product p ON p.product_id = a.product_id
            WHERE l.rn = 1
              AND (a.closed_trade_date IS NULL OR a.closed_trade_date >= ?)
            """
                    .formatted(TradeDateSql.OFFSETS, TradeDateSql.of("x"));

    // 해지(CLOSED)인데 해지 논리 영업일이 없으면 잔액에 남거나 CLOSE가 빠지므로 원천 계약 위반으로 드러낸다(설계 §3-0, §7-5).
    // 잔액 행은 원장이 있는 계좌에만, CLOSE 이벤트는 상품 계좌에만 생기므로 둘 다 아닌 해지 계좌(원장 없는 입출금계좌)는 통과시킨다.
    private static final String CLOSED_WITHOUT_TRADE_DATE_SQL =
            """
            SELECT a.account_id FROM account a
            WHERE a.status = 'CLOSED' AND a.closed_trade_date IS NULL
              AND (a.product_id IS NOT NULL
                   OR EXISTS (SELECT 1 FROM ledger_entry l WHERE l.account_id = a.account_id))
            ORDER BY a.account_id
            LIMIT 5
            """;

    private final JdbcConnectionFactory connections;

    public JdbcReplicaBalanceAdapter(JdbcConnectionFactory connections) {
        this.connections = connections;
    }

    @Override
    public List<BalanceRow> readBalances(LocalDate baseDate) {
        try (Connection replica = connections.open()) {
            replica.setReadOnly(true);
            requireClosedAccountsHaveTradeDate(replica);
            return query(replica, baseDate);
        } catch (SQLException e) {
            throw new EtlStorageException("레플리카 잔액 읽기에 실패했습니다: " + e.getMessage(), e);
        }
    }

    private void requireClosedAccountsHaveTradeDate(Connection replica) throws SQLException {
        List<Long> accountIds = new ArrayList<>();
        try (PreparedStatement statement = replica.prepareStatement(CLOSED_WITHOUT_TRADE_DATE_SQL);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                accountIds.add(resultSet.getLong(1));
            }
        }
        if (!accountIds.isEmpty()) {
            throw new IllegalStateException(
                    "해지 논리 영업일(closed_trade_date)이 없는 해지 계좌가 있습니다(원천 계약 위반): accountId=" + accountIds);
        }
    }

    private List<BalanceRow> query(Connection replica, LocalDate baseDate) throws SQLException {
        List<BalanceRow> rows = new ArrayList<>();
        try (PreparedStatement statement = replica.prepareStatement(SQL)) {
            statement.setObject(1, baseDate);
            statement.setObject(2, baseDate);
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
