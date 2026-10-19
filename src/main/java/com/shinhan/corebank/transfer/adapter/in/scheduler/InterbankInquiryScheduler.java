package com.shinhan.corebank.transfer.adapter.in.scheduler;

import com.shinhan.corebank.transfer.application.port.in.InterbankInquiryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 처리 불명 타행 건을 주기적으로 확정한다(PH-33-②). 배치 락은 두지 않는다 — 여러 인스턴스가 같은 건을 물어도
// 상대 장부는 바뀌지 않고, 확정은 transfer 행 락으로 한 번만 반영된다.
@Component
@RequiredArgsConstructor
public class InterbankInquiryScheduler {

    private final InterbankInquiryUseCase interbankInquiryUseCase;

    @Scheduled(
            fixedDelayString = "${app.interbank.inquiry.fixed-delay}",
            initialDelayString = "${app.interbank.inquiry.fixed-delay}")
    public void settleUnsettled() {
        interbankInquiryUseCase.settleUnsettled();
    }
}
