package com.shinhan.corebank.paymentstop.application.port.out;

import com.shinhan.corebank.paymentstop.domain.gate.LedgerFact;
import java.time.LocalDateTime;
import java.util.List;

// 지급정지 계좌의 원장 거래. transfer.api 로 읽는다.
public interface LedgerFactPort {

    List<LedgerFact> findByAccount(long accountId, LocalDateTime fromInclusive, LocalDateTime toExclusive);
}
