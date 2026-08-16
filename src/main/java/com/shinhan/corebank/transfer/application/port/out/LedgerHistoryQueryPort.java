package com.shinhan.corebank.transfer.application.port.out;

import java.util.List;

import com.shinhan.corebank.transfer.application.port.in.LedgerHistoryQuery;
import com.shinhan.corebank.transfer.domain.LedgerEntry;

public interface LedgerHistoryQueryPort {

    // 페이징·정렬 적용된 목록. 도메인 객체(LedgerEntry)를 반환하고,
    // LedgerHistoryItem으로의 매핑은 서비스가 담당한다.
    List<LedgerEntry> findEntries(LedgerHistoryQuery query);

    // 페이징 무관 전체 건수 (totalPages 계산용)
    long countEntries(LedgerHistoryQuery query);

    // 페이지 합계가 아니라 조회조건(direction 필터 포함) 전체 기준 집계
    LedgerHistoryAggregate summarize(LedgerHistoryQuery query);
}
