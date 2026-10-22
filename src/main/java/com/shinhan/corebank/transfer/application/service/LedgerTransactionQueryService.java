package com.shinhan.corebank.transfer.application.service;

import com.shinhan.corebank.business.api.BusinessDateProvider;
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
     * 거래일 T 의 거래가 발생할 수 있는 날짜 범위는 [직전 영업일 다음 날, T+2일)이다.
     *
     * <p>휴일 거래는 다음 영업일이 거래일이라 직전 영업일 다음 날부터 T 까지 발생한다. COB 가 늦으면 자정을 넘겨 발생한
     * 거래가 아직 전날 거래일에 묶이므로 T+1 일에 발생한 거래도 T 일 수 있다(schema_reference.md ledger_entry).
     * {@code trade_date} 에는 인덱스가 없어서 이 범위로 파티션과 거래번호 인덱스 범위를 좁힌다. 하한을 넉넉히 잡으면
     * 첫 청크가 그 앞 날짜들의 거래번호를 전부 훑은 뒤에야 나온다 — 60b 9/25 기준 14일 하한에서 18.4초, 이 하한에서 4ms.
     */
    static final int MAX_DAYS_BEFORE = 14;

    static final int OCCURRED_DAYS_AFTER = 2;

    private final LedgerTransactionQueryPort ledgerTransactionQueryPort;
    private final BusinessDateProvider businessDateProvider;

    @Override
    public List<LedgerTransactionSummary> findByTradeDate(
            LocalDate tradeDate, String afterTransactionNumber, int limit) {
        if (tradeDate == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
        if (limit < 1) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        LocalDate from = firstDayAfterPreviousBusinessDay(tradeDate);
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

    // 연휴가 아무리 길어도 MAX_DAYS_BEFORE 를 넘겨 거슬러 가지 않는다. 휴일 테이블이 비어 있어도 끝이 있게 한다.
    private LocalDate firstDayAfterPreviousBusinessDay(LocalDate tradeDate) {
        LocalDate from = tradeDate;
        for (int i = 0; i < MAX_DAYS_BEFORE && !businessDateProvider.isBusinessDay(from.minusDays(1)); i++) {
            from = from.minusDays(1);
        }
        return from;
    }
}
