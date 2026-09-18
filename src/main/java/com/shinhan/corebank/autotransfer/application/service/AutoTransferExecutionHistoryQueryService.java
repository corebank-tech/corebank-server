package com.shinhan.corebank.autotransfer.application.service;

import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferExecutionHistoryItem;
import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferExecutionHistoryQueryUseCase;
import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferExecutionHistoryResult;
import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferExecutionHistorySummary;
import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferExecutionHistoryAggregate;
import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferExecutionHistoryQueryPort;
import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferExecutionHistoryRow;
import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferQueryPort;
import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.common.util.PageableResolver;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// UseCase 구현체 — 검증 + 기간 기본값 적용 + Row/Aggregate를 Item/Summary로 변환
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AutoTransferExecutionHistoryQueryService implements AutoTransferExecutionHistoryQueryUseCase {
    private static final Set<Integer> ALLOWED_PAGE_SIZE = Set.of(5, 10, 20, 30, 50);
    private static final int DEFAULT_PERIOD_MONTHS = 1;
    private static final int MAX_RANGE_DAYS = 365;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final AutoTransferExecutionHistoryQueryPort autoTransferExecutionHistoryQueryPort;
    private final AutoTransferQueryPort autoTransferQueryPort;
    private final Clock clock;

    @Override
    public AutoTransferExecutionHistoryResult search(
            Long customerId,
            Long withdrawalAccountId,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size,
            boolean all) {
        if (customerId == null || withdrawalAccountId == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
        Pageable pageable = PageableResolver.resolve(page, size, all, ALLOWED_PAGE_SIZE);
        // REQ-AUTO-018: 조회기간 기본값은 1개월 — toDate 없으면 오늘, fromDate 없으면 toDate-1개월
        LocalDate today = LocalDate.now(clock.withZone(SEOUL));
        LocalDate resolvedToDate = toDate != null ? toDate : today;
        LocalDate resolvedFromDate = fromDate != null ? fromDate : resolvedToDate.minusMonths(DEFAULT_PERIOD_MONTHS);
        if (resolvedFromDate.isAfter(resolvedToDate)) {
            throw new BusinessException(CommonErrorCode.INVALID_DATE_RANGE);
        }
        if (resolvedFromDate.plusDays(MAX_RANGE_DAYS).isBefore(resolvedToDate)) {
            throw new BusinessException(CommonErrorCode.DATE_RANGE_EXCEEDED);
        }

        Page<AutoTransferExecutionHistoryRow> rows = autoTransferExecutionHistoryQueryPort.search(
                customerId, withdrawalAccountId, resolvedFromDate, resolvedToDate, pageable);
        AutoTransferExecutionHistoryAggregate aggregate = autoTransferExecutionHistoryQueryPort.summarize(
                customerId, withdrawalAccountId, resolvedFromDate, resolvedToDate);

        Page<AutoTransferExecutionHistoryItem> itemPage = rows.map(this::toItem);
        List<AutoTransferExecutionHistoryItem> missing = pageable.isUnpaged()
                ? findMissingExecutions(customerId, withdrawalAccountId, resolvedFromDate, resolvedToDate, today)
                : List.of();
        if (!missing.isEmpty()) {
            itemPage = mergeIntoPage(itemPage, missing);
        }
        return new AutoTransferExecutionHistoryResult(itemPage, toSummary(aggregate, missing));
    }

    // 어댑터가 준 Row(out) → Controller가 쓸 Item(in)으로 변환
    private AutoTransferExecutionHistoryItem toItem(AutoTransferExecutionHistoryRow row) {
        return new AutoTransferExecutionHistoryItem(
                row.executionId(),
                row.status(),
                row.executedAt(),
                row.withdrawalAccountId(),
                row.depositAccountNumber(),
                row.payeeName(),
                row.amount(),
                row.cycleMonths(),
                row.myPassbookMemo(),
                row.failureReason());
    }

    // 실행 예정이었는데 이력 없음 감지 -> nextExecutionDate가 오늘보다 과거인 자동이체는 이력이 안남음
    // 페이지 미분할 조회에서만 계산
    private List<AutoTransferExecutionHistoryItem> findMissingExecutions(
            Long customerId, Long withdrawalAccountId, LocalDate fromDate, LocalDate toDate, LocalDate today) {
        List<AutoTransfer> normalAutoTransfers = autoTransferQueryPort
                .search(customerId, withdrawalAccountId, AutoTransferStatus.NORMAL, Pageable.unpaged())
                .getContent();
        return normalAutoTransfers.stream()
                .filter(autoTransfer -> autoTransfer.getNextExecutionDate().isBefore(today))
                .filter(autoTransfer -> !autoTransfer.getNextExecutionDate().isBefore(fromDate)
                        && !autoTransfer.getNextExecutionDate().isAfter(toDate))
                .map(this::toMissingItem)
                .toList();
    }

    private Page<AutoTransferExecutionHistoryItem> mergeIntoPage(
            Page<AutoTransferExecutionHistoryItem> itemPage, List<AutoTransferExecutionHistoryItem> missing) {
        List<AutoTransferExecutionHistoryItem> merged = new ArrayList<>(itemPage.getContent());
        merged.addAll(missing);
        merged.sort(Comparator.comparing(AutoTransferExecutionHistoryItem::executedAt)
                .reversed());
        return new PageImpl<>(merged, Pageable.unpaged(), merged.size());
    }

    private AutoTransferExecutionHistoryItem toMissingItem(AutoTransfer autoTransfer) {
        return new AutoTransferExecutionHistoryItem(
                null,
                ProcessResultStatus.ERROR,
                autoTransfer.getNextExecutionDate().atStartOfDay(),
                autoTransfer.getWithdrawalAccountId(),
                autoTransfer.getDepositAccountNumber(),
                autoTransfer.getPayeeName(),
                autoTransfer.getAmount(),
                autoTransfer.getCycleMonths(),
                autoTransfer.getMyPassbookMemo(),
                "관리자에게 문의해주세요");
    }

    // 어댑터가 준 Aggregate(out) → Controller가 쓸 Summary(in)으로 변환
    private AutoTransferExecutionHistorySummary toSummary(
            AutoTransferExecutionHistoryAggregate aggregate, List<AutoTransferExecutionHistoryItem> missing) {
        long missingCount = missing.size();
        long missingAmount = missing.stream()
                .mapToLong(AutoTransferExecutionHistoryItem::amount)
                .sum();
        return new AutoTransferExecutionHistorySummary(
                aggregate.successCount(),
                aggregate.successAmount(),
                aggregate.errorCount() + missingCount,
                aggregate.errorAmount() + missingAmount);
    }
}
