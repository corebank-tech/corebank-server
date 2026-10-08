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
     * 예약이체)가 끝난 뒤여야 그날 기표가 모두 끝난 상태에서 의미 있게 돈다. 02:00은 그저
     * "보통 이쯤이면 끝나있겠지"로 잡은 트리거 시각일 뿐이고, 실제 완료 여부는
     * {@link com.shinhan.corebank.transfer.application.service.LedgerReconciliationBatchService}가
     * DAILY_TRANSFER_BATCH의 실행 상태를 직접 확인해서 판단한다(PR #463 리뷰 - 배치 담당자 제안).
     */
    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul")
    public void runDailyReconciliation() {
        ledgerReconciliationBatchUseCase.run(
                LocalDate.now(clock.withZone(SEOUL)).minusDays(1));
    }

    /**
     * 전 계좌 전수 대사(#468). 원장 기표가 없어 매일 도는 증분 대사가 보지 못하는 휴면 계좌의 드리프트를 잡는다.
     * 계좌 수에 비례해 비싸므로 주 1회, 이체가 가장 적고 02:00 증분 대사와 겹치지 않는 일요일 03:00에 돈다.
     */
    @Scheduled(cron = "0 0 3 * * SUN", zone = "Asia/Seoul")
    public void runWeeklyFullReconciliation() {
        ledgerReconciliationBatchUseCase.runFull();
    }
}
