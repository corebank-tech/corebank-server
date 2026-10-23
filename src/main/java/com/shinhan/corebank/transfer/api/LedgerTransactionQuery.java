package com.shinhan.corebank.transfer.api;

import java.time.LocalDate;
import java.util.List;

/**
 * 거래일 하루의 원장 거래를 거래번호 순으로 읽는 계약(PH-28b). GL 원장↔전표 대사가 쓴다.
 *
 * <p>개시({@code OPENING})와 QA 시드({@code QA_SEED_INITIAL})는 전표 대사 대상이 아니라 내지 않는다. 나머지 유형은
 * 전부 내고, 무엇을 대사할지는 호출자가 정한다. {@code trade_date} 가 NULL 인 행(#516 이전)은 어느 거래일에도
 * 걸리지 않는다.
 *
 * <p>호출자 트랜잭션에 참여한다. 전표와 같은 스냅샷에서 비교하려면 호출자가 연 트랜잭션 안에서 부른다.
 */
public interface LedgerTransactionQuery {

    /**
     * @param afterTransactionNumber 이 거래번호 다음부터 읽는다. 첫 청크는 {@code null}
     * @param limit 최대 건수. 1 이상
     */
    List<LedgerTransactionSummary> findByTradeDate(LocalDate tradeDate, String afterTransactionNumber, int limit);
}
