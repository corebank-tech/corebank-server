package com.shinhan.corebank.transfer.adapter.in.scheduler;

import com.shinhan.corebank.transfer.application.port.in.LedgerReconciliationBatchUseCase;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LedgerReconciliationScheduler {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final LedgerReconciliationBatchUseCase ledgerReconciliationBatchUseCase;
    private final Clock clock;

    /**
     * 전일(date) 원장-잔액 대사. batch 모듈의 DailyTransferBatchScheduler(00:10 시작, 자동이체→
     * 예약이체)가 끝난 뒤여야 그날 기표가 모두 끝난 상태에서 의미 있게 돈다. 정확한 소요시간을
     * 실측할 신호가 없어 02:00으로 넉넉히 잡은 휴리스틱값이다 — 배치 소요시간이 이보다 길어지면
     * 조정이 필요하다.
     */
    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul")
    public void runDailyReconciliation() {
        ledgerReconciliationBatchUseCase.run(
                LocalDate.now(clock.withZone(SEOUL)).minusDays(1));
    }
}
