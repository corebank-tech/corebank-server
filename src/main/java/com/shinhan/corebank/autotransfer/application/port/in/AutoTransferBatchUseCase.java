package com.shinhan.corebank.autotransfer.application.port.in;

import java.time.LocalDate;

public interface AutoTransferBatchUseCase {
    // 지정한 날짜를 실행일로 하는 자동이체 배치를 돈다. @Scheduled는 이 메서드를 오늘 날짜로 호출하는
    // 얇은 어댑터로만 두고, 로컬/수동 실행 시엔 날짜를 바꿔가며 직접 호출할 수 있게 원시 파라미터로 받는다.
    void executeDaily(LocalDate date);
}
