package com.shinhan.corebank.business.application.port.out;

import java.time.LocalDate;
import java.time.LocalDateTime;

public interface BusinessDateCutoffPort {

    // 이미 있으면 그대로 둔다 — 재실행이나 다른 인스턴스가 먼저 넘긴 경우에도 같은 호출로 처리한다
    void recordClosed(LocalDate baseDate, LocalDateTime closedAt);
}
