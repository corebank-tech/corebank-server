package com.shinhan.corebank.transfer.application.port.out;

import com.shinhan.corebank.transfer.api.LedgerTransactionSummary;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface LedgerTransactionQueryPort {

    /**
     * 거래일이 {@code tradeDate} 인 원장 거래를 거래번호 순으로 읽는다. 발생 시각과 거래번호 범위는 인덱스와 파티션을
     * 좁히는 조건이고, 거래일 판정은 {@code trade_date} 가 한다.
     */
    List<LedgerTransactionSummary> findByTradeDate(
            LocalDate tradeDate,
            LocalDateTime occurredFromInclusive,
            LocalDateTime occurredToExclusive,
            String transactionNumberFromInclusive,
            String transactionNumberToExclusive,
            String afterTransactionNumber,
            int limit);
}
