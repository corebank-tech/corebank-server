package com.shinhan.corebank.business.application.port.out;

import com.shinhan.corebank.business.domain.BusinessDateType;
import java.time.LocalDate;

public interface BusinessDateCommandPort {

    // 저장값이 expected 일 때만 newDate 로 바꾼다 — 동시 호출에도 한 번만 바뀐다
    boolean compareAndSet(BusinessDateType type, LocalDate expected, LocalDate newDate);
}
