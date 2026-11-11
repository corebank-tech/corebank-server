package com.shinhan.corebank.fds.application.service;

import com.shinhan.corebank.transfer.api.RecentTransferAggregate;
import com.shinhan.corebank.transfer.api.RecentTransferQuery;
import com.shinhan.corebank.transfer.api.TransferHoldContext;
import com.shinhan.corebank.transfer.api.TransferHoldDecision;
import com.shinhan.corebank.transfer.api.TransferHoldPolicy;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 점수 API와 무관하게 최근 성공 이체를 읽어 정책 B를 원장 기표 전에 판정한다. */
@Component
@RequiredArgsConstructor
public class FdsRepeatTransferHoldPolicy implements TransferHoldPolicy {
    private static final Duration WINDOW = Duration.ofMinutes(10);
    private final RecentTransferQuery transfers;

    @Value("${app.fds.policy.enabled:false}")
    private boolean enabled;

    @Override
    public int order() {
        return 100;
    }

    @Override
    public TransferHoldDecision decide(TransferHoldContext context) {
        // 스위치가 꺼지면 고객 락 안에서 조회도 실행하지 않는다.
        if (!enabled) return TransferHoldDecision.proceed();
        RecentTransferAggregate previous = transfers.aggregateSucceeded(
                context.customerId(),
                context.depositAccountNumber(),
                context.requestedAt().minus(WINDOW));
        long total = Math.addExact(previous.totalAmount(), context.amount());
        if (previous.count() >= 2 && total >= 3_000_000L) {
            return TransferHoldDecision.holdForApproval("B");
        }
        return TransferHoldDecision.proceed();
    }
}
