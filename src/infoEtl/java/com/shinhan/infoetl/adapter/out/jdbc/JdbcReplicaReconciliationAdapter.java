package com.shinhan.infoetl.adapter.out.jdbc;

import com.shinhan.infoetl.application.port.out.EtlStorageException;
import com.shinhan.infoetl.application.port.out.ReplicaReconciliationPort;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

public final class JdbcReplicaReconciliationAdapter implements ReplicaReconciliationPort {

    // 잔액 합계가 아니라 기표 금액의 부호 합이라 balance_after로 구한 마트 합계를 독립적으로 검증한다(설계 §3-3, §4).
    private static final String LEDGER_TOTAL_SQL =
            """
            WITH RECURSIVE %s
            SELECT COALESCE(SUM(CASE l.direction WHEN 'DEPOSIT' THEN l.amount ELSE -l.amount END), 0)
            FROM ledger_entry l
            WHERE %s <= ?
            """
                    .formatted(TradeDateSql.OFFSETS, TradeDateSql.of("l"));

    private static final String GL_MOVEMENT_SQL =
            """
            SELECT COALESCE(SUM(CASE dr_cr WHEN 'CREDIT' THEN amount ELSE -amount END), 0)
            FROM gl_journal_entry
            WHERE account_code = '20100' AND trade_date > ? AND trade_date <= ?
            """;

    private final JdbcConnectionFactory connections;

    public JdbcReplicaReconciliationAdapter(JdbcConnectionFactory connections) {
        this.connections = connections;
    }

    @Override
    public long readLedgerTotal(LocalDate baseDate) {
        return readLong("원장 합계", LEDGER_TOTAL_SQL, baseDate);
    }

    @Override
    public long readGlDepositMovement(LocalDate fromExclusive, LocalDate toInclusive) {
        return readLong("GL 증감", GL_MOVEMENT_SQL, fromExclusive, toInclusive);
    }

    private long readLong(String what, String sql, LocalDate... parameters) {
        try (Connection replica = connections.open()) {
            replica.setReadOnly(true);
            try (PreparedStatement statement = replica.prepareStatement(sql)) {
                for (int index = 0; index < parameters.length; index++) {
                    statement.setObject(index + 1, parameters[index]);
                }
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    return resultSet.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new EtlStorageException("레플리카 " + what + " 읽기에 실패했습니다: " + e.getMessage(), e);
        }
    }
}
