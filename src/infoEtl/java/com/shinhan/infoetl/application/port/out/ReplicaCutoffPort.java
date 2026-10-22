package com.shinhan.infoetl.application.port.out;

import java.time.LocalDate;
import java.util.Optional;

// 원천이 영업일 전환과 같은 트랜잭션으로 남기는 business_date_cutoff를 레플리카에서 읽는다.
public interface ReplicaCutoffPort {

    // base_date = D, status = 'CLOSED' 행이 보이는가
    boolean isClosed(LocalDate baseDate);

    // CLOSED인 가장 최근 base_date. cutoff가 하나도 없으면 비어 있다.
    Optional<LocalDate> latestClosedDate();
}
