package com.shinhan.corebank.gl.application.port.out;

import com.shinhan.corebank.gl.domain.LedgerTransaction;
import java.time.LocalDate;
import java.util.List;

public interface LedgerTransactionReadPort {

    /** 거래일 하루의 원장 거래를 거래번호 순으로 {@code limit} 건씩 읽는다. 첫 청크는 {@code afterTransactionNumber = null}. */
    List<LedgerTransaction> findByTradeDate(LocalDate tradeDate, String afterTransactionNumber, int limit);
}
