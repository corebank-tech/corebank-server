package com.shinhan.corebank.autotransfer.application.port.in;

import java.time.LocalDate;

public interface AutoTransferBatchUseCase {
    // 지정한 날짜를 실행일로 하는 자동이체 배치를 돈다. @Scheduled는 이 메서드를 오늘 날짜로 호출하는
    // 얇은 어댑터로만 두고, 로컬/수동 실행 시엔 날짜를 바꿔가며 직접 호출할 수 있게 원시 파라미터로 받는다.
    void executeDaily(LocalDate date);

    // PROCESSING에 멈춘 회차 전체를 찾아 실제 거래 여부를 확인하고 SUCCESS/ERROR로 확정한다.
    // 대상은 status=PROCESSING 전체(날짜 무관) - date는 로그 표시용으로만 쓴다.
    void reconcileStuckExecutions(LocalDate date);
}
