package com.shinhan.corebank.business.application.port.out;

import com.shinhan.corebank.business.domain.BusinessDateType;
import java.time.LocalDate;
import java.util.Optional;

public interface BusinessDateCommandPort {

    // 저장값이 expected 일 때만 newDate 로 바꾼다 — 동시 호출에도 한 번만 바뀐다
    boolean compareAndSet(BusinessDateType type, LocalDate expected, LocalDate newDate);

    // 락을 거는 읽기 — REPEATABLE READ 스냅샷이 아니라 커밋된 최신 값을 본다
    Optional<LocalDate> findBusinessDateForUpdate(BusinessDateType type);
}
