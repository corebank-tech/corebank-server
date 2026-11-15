package com.shinhan.corebank.fds.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.fds.api.FdsInvestigationJob;
import com.shinhan.corebank.fds.application.port.out.FdsInvestigationJobPort;
import com.shinhan.corebank.fds.application.port.out.FdsInvestigationJobPort.HistorySnapshotItem;
import com.shinhan.corebank.fds.domain.exception.FdsErrorCode;
import com.shinhan.corebank.transfer.api.RecipientHistory;
import com.shinhan.corebank.transfer.api.SucceededTransfer;
import com.shinhan.corebank.transfer.api.SucceededTransferHistoryQuery;
import com.shinhan.corebank.transfer.api.TransferHoldForFds;
import com.shinhan.corebank.transfer.api.TransferHoldUseCase;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 조사 작업에 묶인 고객 범위로만 P4 성공 이력을 제공한다. */
@Service
@RequiredArgsConstructor
public class FdsAgentToolService {
    private final FdsInvestigationJobPort jobs;
    private final TransferHoldUseCase holds;
    private final SucceededTransferHistoryQuery history;
    private final Clock clock;

    public HistoryData history(long holdId, int attemptNo) {
        Context context = context(holdId, attemptNo);
        LocalDateTime to = context.hold().heldAt();
        LocalDateTime from = to.minusHours(24);
        List<SucceededTransfer> found = history.findRecent(context.hold().customerId(), from, to, 101);
        boolean hasMore = found.size() > 100;
        List<HistoryItem> items =
                found.stream().limit(100).map(FdsAgentToolService::item).toList();
        jobs.recordHistorySnapshot(
                context.job().investigationId(),
                holdId,
                attemptNo,
                context.hold().customerId(),
                from,
                to,
                hasMore,
                items.stream()
                        .map(item -> new HistorySnapshotItem(
                                item.refId(), item.amount(), item.transferredAt(), item.recipientAccountMasked()))
                        .toList(),
                LocalDateTime.now(clock));
        return new HistoryData(holdId, items, hasMore);
    }

    public RecipientData recipient(long holdId, int attemptNo) {
        Context context = context(holdId, attemptNo);
        RecipientHistory summary = history.summarizeRecipient(
                context.hold().customerId(),
                context.hold().depositAccountNumber(),
                context.hold().withdrawalAccountId());
        boolean beforeWindow = summary.firstSucceededAt() != null
                && summary.firstSucceededAt().isBefore(context.hold().heldAt().minusMinutes(10));
        jobs.recordRecipientSnapshot(
                context.job().investigationId(),
                holdId,
                attemptNo,
                context.hold().customerId(),
                summary.firstSucceededAt(),
                beforeWindow,
                summary.otherWithdrawalAccountCount(),
                LocalDateTime.now(clock));
        return new RecipientData(
                holdId, beforeWindow, summary.firstSucceededAt(), summary.otherWithdrawalAccountCount());
    }

    private Context context(long holdId, int attemptNo) {
        FdsInvestigationJob job = jobs.findByHoldId(holdId);
        if (job == null || !"RUNNING".equals(job.status()) || job.attemptCount() != attemptNo) {
            throw new BusinessException(FdsErrorCode.NOT_FOUND);
        }
        TransferHoldForFds hold = holds.findForFds(holdId);
        if (hold == null || hold.customerId() <= 0) throw new BusinessException(FdsErrorCode.NOT_FOUND);
        return new Context(job, hold);
    }

    private static HistoryItem item(SucceededTransfer transfer) {
        return new HistoryItem(
                transfer.refId(), transfer.amount(), transfer.transferredAt(), mask(transfer.depositAccountNumber()));
    }

    private static String mask(String value) {
        if (value == null || value.length() < 4) return "****";
        return "***-**-" + value.substring(value.length() - 4);
    }

    private record Context(FdsInvestigationJob job, TransferHoldForFds hold) {}

    public record HistoryData(long transferHoldId, List<HistoryItem> items, boolean hasMore) {}

    public record HistoryItem(long refId, long amount, LocalDateTime transferredAt, String recipientAccountMasked) {}

    public record RecipientData(
            long transferHoldId,
            boolean hasSucceededBeforeWindow,
            LocalDateTime firstSucceededAt,
            int otherWithdrawalAccountCount) {}
}
