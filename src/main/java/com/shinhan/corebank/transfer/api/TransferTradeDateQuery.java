package com.shinhan.corebank.transfer.api;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

/**
 * 예약·자동이체 회차가 만든 이체 행의 거래일을 읽는 계약(#679). 처리결과 화면이 실행 시각 옆에 거래일을 보여줄 때 쓴다.
 *
 * 성공·실패(ERROR) 행 모두 거래일이 있다. 이체 행이 없는 회차(취소, 이체 전 실패)와 거래일이 NULL 인 행(#516 이전)은
 * 결과 Map 에 키가 없다.
 *
 * 호출자 트랜잭션에 참여한다.
 */
public interface TransferTradeDateQuery {

    Map<SourceExecutionKey, LocalDate> findScheduledTradeDates(Collection<SourceExecutionKey> keys);

    Map<SourceExecutionKey, LocalDate> findAutoTradeDates(Collection<SourceExecutionKey> keys);
}
