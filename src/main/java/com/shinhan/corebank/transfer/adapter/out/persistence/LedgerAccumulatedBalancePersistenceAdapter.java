package com.shinhan.corebank.transfer.adapter.out.persistence;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.transfer.application.port.out.LedgerAccumulatedBalancePort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class LedgerAccumulatedBalancePersistenceAdapter implements LedgerAccumulatedBalancePort {

    private static final String SQL =
            """
            WITH ids AS (
                SELECT jt.account_id
                FROM JSON_TABLE(
                    :idsJson,
                    '$[*]'
                    COLUMNS (account_id BIGINT PATH '$')
                ) AS jt
            ),
            period_ranked AS (
                SELECT
                    le.account_id,
                    DATE(le.occurred_at) AS balance_date,
                    le.balance_after,
                    ROW_NUMBER() OVER (
                        PARTITION BY
                            le.account_id,
                            DATE(le.occurred_at)
                        ORDER BY
                            le.occurred_at DESC,
                            le.ledger_entry_id DESC
                    ) AS rn
                FROM ledger_entry le
                JOIN ids i
                    ON i.account_id = le.account_id
                WHERE le.occurred_at >= :fromTimestamp
                  AND le.occurred_at < :toTimestamp
            ),
            events AS (
                SELECT
                    i.account_id,
                    :fromDate AS balance_date,
                    COALESCE((
                        SELECT before_le.balance_after
                        FROM ledger_entry before_le
                        WHERE before_le.account_id = i.account_id
                          AND before_le.occurred_at < :fromTimestamp
                        ORDER BY
                            before_le.occurred_at DESC,
                            before_le.ledger_entry_id DESC
                        LIMIT 1
                    ), 0) AS balance_after,
                    0 AS priority
                FROM ids i

                UNION ALL

                SELECT
                    account_id,
                    balance_date,
                    balance_after,
                    1 AS priority
                FROM period_ranked
                WHERE rn = 1
            ),
            deduplicated AS (
                SELECT
                    account_id,
                    balance_date,
                    balance_after,
                    ROW_NUMBER() OVER (
                        PARTITION BY account_id, balance_date
                        ORDER BY priority DESC
                    ) AS rn
                FROM events
            ),
            spans AS (
                SELECT
                    account_id,
                    balance_date,
                    balance_after,
                    COALESCE(
                        LEAD(balance_date) OVER (
                            PARTITION BY account_id
                            ORDER BY balance_date
                        ),
                        :toDate
                    ) AS next_date
                FROM deduplicated
                WHERE rn = 1
            )
            SELECT
                account_id,
                SUM(
                    CAST(balance_after AS DECIMAL(38, 0))
                    * DATEDIFF(next_date, balance_date)
                ) AS accumulated_balance
            FROM spans
            GROUP BY account_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public Map<Long, Long> calculate(List<Long> accountIds, LocalDate fromInclusive, LocalDate toExclusive) {

        if (accountIds == null
                || accountIds.stream().anyMatch(id -> id == null || id <= 0)
                || fromInclusive == null
                || toExclusive == null) {

            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }

        if (fromInclusive.isAfter(toExclusive)) {
            throw new BusinessException(CommonErrorCode.INVALID_DATE_RANGE);
        }

        if (accountIds.isEmpty()) {
            return Map.of();
        }

        if (fromInclusive.equals(toExclusive)) {
            Map<Long, Long> zero = new LinkedHashMap<>();

            accountIds.forEach(id -> zero.put(id, 0L));

            return zero;
        }

        String idsJson = accountIds.stream().distinct().map(String::valueOf).collect(Collectors.joining(",", "[", "]"));

        MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("idsJson", idsJson)
        .addValue("fromDate", fromInclusive)
        .addValue("toDate", toExclusive)
        .addValue("fromTimestamp", fromInclusive.atStartOfDay())
        .addValue("toTimestamp", toExclusive.atStartOfDay());

        Map<Long, Long> result = new LinkedHashMap<>();

        accountIds.forEach(id -> result.put(id, 0L));

        jdbc.query(SQL, params, rs -> {
            long accountId = rs.getLong("account_id");

            BigDecimal accumulated = rs.getBigDecimal("accumulated_balance");

            result.put(accountId, accumulated == null ? 0L : accumulated.longValueExact());
        });

        return result;
    }
}
