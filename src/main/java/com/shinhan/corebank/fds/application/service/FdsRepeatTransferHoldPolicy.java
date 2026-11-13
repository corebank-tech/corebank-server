package com.shinhan.corebank.fds.application.service;

import com.shinhan.corebank.transfer.api.RecentSuccessfulTransfer;
import com.shinhan.corebank.transfer.api.RecentTransferQuery;
import com.shinhan.corebank.transfer.api.TransferHoldContext;
import com.shinhan.corebank.transfer.api.TransferHoldDecision;
import com.shinhan.corebank.transfer.api.TransferHoldEvidence;
import com.shinhan.corebank.transfer.api.TransferHoldPolicy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 저장된 행동과 성공 이체만 읽어 정책 A를 우선하고, 불성립하면 정책 B를 판정한다. */
@Component
@RequiredArgsConstructor
public class FdsRepeatTransferHoldPolicy implements TransferHoldPolicy {
    private static final Duration WINDOW = Duration.ofMinutes(10);
    private final RecentTransferQuery transfers;
    private final BehaviorHesitationService hesitation;

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
        List<RecentSuccessfulTransfer> previous = transfers.findSucceeded(
                context.customerId(),
                context.depositAccountNumber(),
                context.requestedAt().minus(WINDOW));
        int count = Math.addExact(previous.size(), 1);
        long total = context.amount();
        for (RecentSuccessfulTransfer transfer : previous) {
            total = Math.addExact(total, transfer.amount());
        }

        boolean policyAThreshold = count >= 3 && total >= 1_500_000L;
        if (policyAThreshold) {
            LocalDateTime windowStart = previous.isEmpty()
                    ? context.requestedAt()
                    : previous.getFirst().transferredAt();
            boolean newRecipient =
                    !transfers.hasSucceededBefore(context.customerId(), context.depositAccountNumber(), windowStart);
            boolean hasHesitation = hesitation.anyHesitation(
                    context.customerId(),
                    context.flowId(),
                    previous.stream().map(RecentSuccessfulTransfer::refId).toList());
            if (newRecipient && hasHesitation) {
                return TransferHoldDecision.holdForApproval("A", new TransferHoldEvidence(count, total, true, true));
            }
            if (count >= 3 && total >= 3_000_000L) {
                return TransferHoldDecision.holdForApproval(
                        "B", new TransferHoldEvidence(count, total, hasHesitation, newRecipient));
            }
        }
        return TransferHoldDecision.proceed();
    }
}
