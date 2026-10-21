package com.shinhan.infoetl.application.port.out;

import com.shinhan.infoetl.domain.SubscriptionEventRow;
import java.time.LocalDate;
import java.util.List;

public interface ReplicaEventPort {

    // 거래일이 baseDate인 가입(SUBSCRIBE)과 해지(CLOSE) 이벤트. 그날 이벤트가 없으면 빈 목록이다.
    List<SubscriptionEventRow> readSubscriptionEvents(LocalDate baseDate);
}
