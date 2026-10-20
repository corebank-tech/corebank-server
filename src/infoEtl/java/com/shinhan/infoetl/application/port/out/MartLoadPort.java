package com.shinhan.infoetl.application.port.out;

import com.shinhan.infoetl.domain.LoadResult;
import com.shinhan.infoetl.domain.MartBalanceRow;
import com.shinhan.infoetl.domain.MartEventRow;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

public interface MartLoadPort {

    boolean isCompleted(LocalDate baseDate);

    // staleAfter보다 오래 RUNNING인 기록을 FAILED로 정리하고 새 RUNNING 기록을 남긴다. 이 호출은 뒤 단계가 실패해도 남는다.
    long start(LocalDate baseDate, Duration staleAfter);

    // 기준일의 기존 잔액·이벤트 삭제, 삽입, asOf 계산과 완료 표시를 한 트랜잭션으로 반영한다. 실패하면 모두 되돌린다.
    LoadResult publish(
            long loadBatchId,
            LocalDate baseDate,
            List<MartBalanceRow> balances,
            List<MartEventRow> events,
            AsOfCalculator asOfCalculator);

    void fail(long loadBatchId, String reason);
}
