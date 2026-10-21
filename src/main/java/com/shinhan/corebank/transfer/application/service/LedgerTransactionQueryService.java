package com.shinhan.corebank.transfer.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.transfer.api.LedgerTransactionQuery;
import com.shinhan.corebank.transfer.api.LedgerTransactionSummary;
import com.shinhan.corebank.transfer.application.port.out.LedgerTransactionQueryPort;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LedgerTransactionQueryService implements LedgerTransactionQuery {

    /**
     * 거래일 T 의 거래가 발생할 수 있는 날짜 범위 [T-14일, T+2일).
     *
     * <p>휴일 거래는 다음 영업일이 거래일이라 거래일보다 연휴 길이만큼 먼저 발생한다. COB 가 늦으면 자정을 넘겨 발생한
     * 거래가 아직 전날 거래일에 묶인다(schema_reference.md ledger_entry). {@code trade_date} 에는 인덱스가 없어서 이
     * 범위로 파티션과 거래번호 인덱스 범위를 좁힌다.
     */
    static final int OCCURRED_DAYS_BEFORE = 14;

    static final int OCCURRED_DAYS_AFTER = 2;

    private final LedgerTransactionQueryPort ledgerTransactionQueryPort;

    @Override
    public List<LedgerTransactionSummary> findByTradeDate(
            LocalDate tradeDate, String afterTransactionNumber, int limit) {
        if (tradeDate == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
        if (limit < 1) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        LocalDate from = tradeDate.minusDays(OCCURRED_DAYS_BEFORE);
        LocalDate to = tradeDate.plusDays(OCCURRED_DAYS_AFTER);
        // 거래번호는 날짜 8자리로 시작한다 — 런타임은 요청일(SequenceGenerator), 60b 시드는 거래일. 둘 다 위 범위 안이다.
        return ledgerTransactionQueryPort.findByTradeDate(
                tradeDate,
                from.atStartOfDay(),
                to.atStartOfDay(),
                from.format(DateTimeFormatter.BASIC_ISO_DATE),
                to.format(DateTimeFormatter.BASIC_ISO_DATE),
                afterTransactionNumber == null ? "" : afterTransactionNumber,
                limit);
    }
}
