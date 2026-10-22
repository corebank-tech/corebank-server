package com.shinhan.corebank.transfer.adapter.out.persistence;

import com.shinhan.corebank.transfer.api.LedgerTransactionSummary;
import com.shinhan.corebank.transfer.application.port.out.LedgerTransactionQueryPort;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class LedgerTransactionQueryPersistenceAdapter implements LedgerTransactionQueryPort {

    // occurred_at 범위는 파티션을, 거래번호 범위와 커서는 ix_le_txno 순서 스캔을 쓰게 한다. trade_date 는 판정만 한다.
    // 금액은 행 하나의 값(MIN)이다. 2행 거래를 SUM 하면 두 배가 된다. 한 거래번호의 행은 같은 유형을 가진다 —
    // 기존 거래번호에 행을 덧붙이는 곳은 타행 환불(LedgerEntry.forRefund) 하나이고 원거래 유형을 그대로 복사하며,
    // 취소정정은 새 거래번호를 채번한다. 다른 유형을 같은 번호에 붙이는 경로가 생기면 MIN 이 하나만 고르게 된다.
    static final String FIND_BY_TRADE_DATE_SQL =
            """
            SELECT le.transaction_number    AS transaction_number,
                   MIN(le.transaction_type) AS transaction_type,
                   MIN(le.amount)           AS amount
            FROM ledger_entry le
            WHERE le.trade_date = :tradeDate
              AND le.occurred_at >= :occurredFrom
              AND le.occurred_at <  :occurredTo
              AND le.transaction_number >= :transactionNumberFrom
              AND le.transaction_number <  :transactionNumberTo
              AND le.transaction_number >  :after
              AND le.transaction_type NOT IN ('OPENING', 'QA_SEED_INITIAL')
            GROUP BY le.transaction_number
            ORDER BY le.transaction_number
            LIMIT :limit
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public List<LedgerTransactionSummary> findByTradeDate(
            LocalDate tradeDate,
            LocalDateTime occurredFromInclusive,
            LocalDateTime occurredToExclusive,
            String transactionNumberFromInclusive,
            String transactionNumberToExclusive,
            String afterTransactionNumber,
            int limit) {
        Map<String, Object> params = Map.of(
                "tradeDate", tradeDate,
                "occurredFrom", occurredFromInclusive,
                "occurredTo", occurredToExclusive,
                "transactionNumberFrom", transactionNumberFromInclusive,
                "transactionNumberTo", transactionNumberToExclusive,
                "after", afterTransactionNumber,
                "limit", limit);
        return jdbcTemplate.query(
                FIND_BY_TRADE_DATE_SQL,
                params,
                (rs, rowNum) -> new LedgerTransactionSummary(
                        rs.getString("transaction_number"),
                        rs.getString("transaction_type"),
                        tradeDate,
                        rs.getLong("amount")));
    }
}
