package com.shinhan.corebank.transfer.adapter.in.scheduler;

import com.shinhan.corebank.transfer.application.port.in.TransferHoldReleaseBatchUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 지연이체 해제 주기. 예약이체 배치(하루 1회)는 시간 단위 지연에 맞지 않아 따로 둔다(D-34).
@Component
@RequiredArgsConstructor
public class TransferHoldReleaseScheduler {

    private final TransferHoldReleaseBatchUseCase transferHoldReleaseBatchUseCase;

    @Scheduled(
            fixedDelayString = "${app.delayed-transfer.release.fixed-delay}",
            initialDelayString = "${app.delayed-transfer.release.fixed-delay}")
    public void releaseDue() {
        transferHoldReleaseBatchUseCase.releaseDue();
    }
}
