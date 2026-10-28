package com.shinhan.infoapi.adapter.out.jdbc;

import com.shinhan.infoapi.application.port.out.MartReadException;
import com.shinhan.infoapi.application.port.out.MartReadPort;
import com.shinhan.infoapi.domain.DepositBalancePoint;
import com.shinhan.infoapi.domain.ProductSubscriptionLine;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcMartReadAdapter implements MartReadPort {

    // 마트가 느려져도 작업 스레드가 오래 묶이지 않게 쿼리마다 건다.
    private static final int QUERY_TIMEOUT_SECONDS = 10;

    // completed_at 이 가장 늦은 COMPLETED 배치의 워터마크다(info_mart.md §5-4). 그 행의 워터마크가 NULL 이면 이력 없음으로 본다.
    private static final String LATEST_AS_OF =
            """
            SELECT as_of_date FROM mart_load_batch
            WHERE status = 'COMPLETED'
            ORDER BY completed_at DESC, load_batch_id DESC LIMIT 1
            """;

    // 기준일마다 COMPLETED 는 하나뿐이다(재적재 때 이전 배치가 REPLACED 로 바뀐다). mart_balance_total 이 NULL 인 배치는 뺀다.
    private static final String DEPOSIT_BALANCES =
            """
            SELECT base_date, mart_balance_total FROM mart_load_batch
            WHERE status = 'COMPLETED' AND mart_balance_total IS NOT NULL AND base_date BETWEEN ? AND ?
            ORDER BY base_date
            """;

    // 상품명·코드·그룹은 상품별로 가장 최근 이벤트 행의 스냅샷이다(info_mart.md §5-3).
    // 같은 날이면 기본키(base_date, event_type, account_key) 순서로 한 행만 골라 결과가 흔들리지 않는다.
    private static final String PRODUCT_SUBSCRIPTIONS =
            """
            SELECT e.product_id,
                   latest.product_code, latest.product_name, latest.product_group,
                   SUM(e.event_type = 'SUBSCRIBE') AS subscribed_count,
                   SUM(e.event_type = 'CLOSE') AS closed_count
            FROM mart_product_subscription_event e
            JOIN (
                SELECT product_id, product_code, product_name, product_group,
                       ROW_NUMBER() OVER (
                           PARTITION BY product_id
                           ORDER BY base_date DESC, event_type DESC, account_key DESC) AS rn
                FROM mart_product_subscription_event
            ) latest ON latest.product_id = e.product_id AND latest.rn = 1
            WHERE e.base_date BETWEEN ? AND ?
            GROUP BY e.product_id, latest.product_code, latest.product_name, latest.product_group
            ORDER BY e.product_id
            """;

    private final JdbcConnectionFactory connections;

    public JdbcMartReadAdapter(JdbcConnectionFactory connections) {
        this.connections = connections;
    }

    @Override
    public Optional<LocalDate> latestAsOf() {
        try (Connection connection = connections.open();
                PreparedStatement statement = connection.prepareStatement(LATEST_AS_OF)) {
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                Date asOf = rows.getDate(1);
                return asOf == null ? Optional.empty() : Optional.of(asOf.toLocalDate());
            }
        } catch (SQLException e) {
            throw new MartReadException("마트 asOf 조회에 실패했습니다.", e);
        }
    }

    @Override
    public List<DepositBalancePoint> depositBalances(LocalDate from, LocalDate to) {
        try (Connection connection = connections.open();
                PreparedStatement statement = connection.prepareStatement(DEPOSIT_BALANCES)) {
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            statement.setDate(1, Date.valueOf(from));
            statement.setDate(2, Date.valueOf(to));
            try (ResultSet rows = statement.executeQuery()) {
                List<DepositBalancePoint> points = new ArrayList<>();
                while (rows.next()) {
                    points.add(new DepositBalancePoint(rows.getDate(1).toLocalDate(), rows.getLong(2)));
                }
                return points;
            }
        } catch (SQLException e) {
            throw new MartReadException("마트 일별 잔액 조회에 실패했습니다.", e);
        }
    }

    @Override
    public List<ProductSubscriptionLine> productSubscriptions(LocalDate from, LocalDate to) {
        try (Connection connection = connections.open();
                PreparedStatement statement = connection.prepareStatement(PRODUCT_SUBSCRIPTIONS)) {
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            statement.setDate(1, Date.valueOf(from));
            statement.setDate(2, Date.valueOf(to));
            try (ResultSet rows = statement.executeQuery()) {
                List<ProductSubscriptionLine> lines = new ArrayList<>();
                while (rows.next()) {
                    lines.add(new ProductSubscriptionLine(
                            rows.getLong("product_id"),
                            rows.getString("product_code"),
                            rows.getString("product_name"),
                            rows.getString("product_group"),
                            rows.getLong("subscribed_count"),
                            rows.getLong("closed_count")));
                }
                return lines;
            }
        } catch (SQLException e) {
            throw new MartReadException("마트 가입·해지 조회에 실패했습니다.", e);
        }
    }
}
