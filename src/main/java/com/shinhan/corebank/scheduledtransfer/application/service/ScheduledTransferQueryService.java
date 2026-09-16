package com.shinhan.corebank.scheduledtransfer.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.common.util.PageableResolver;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferExecutionResultItem;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferExecutionResultPage;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferExecutionResultSort;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferExecutionResultSummary;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferListItem;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferQueryUseCase;
import com.shinhan.corebank.scheduledtransfer.application.port.out.AccountStatusPort;
import com.shinhan.corebank.scheduledtransfer.application.port.out.ScheduledTransferExecutionResultAggregate;
import com.shinhan.corebank.scheduledtransfer.application.port.out.ScheduledTransferQueryPort;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransfer;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduledTransferQueryService implements ScheduledTransferQueryUseCase {
    private static final Set<Integer> ALLOWED_PAGE_SIZE = Set.of(5, 10, 20, 30, 50);
    private static final int MAX_RANGE_DAYS = 365;
    private static final int MAX_ALL_QUERY_SIZE = 100;
    private static final int DEFAULT_PERIOD_MONTHS = 1;

    private final ScheduledTransferQueryPort scheduledTransferQueryPort;
    private final AccountStatusPort accountStatusPort;
    private final Clock clock;

    @Override
    public Page<ScheduledTransferListItem> search(
            Long customerId,
            ScheduledTransferStatus status,
            Long withdrawalAccountId,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size,
            boolean all) {
        if (customerId == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
        Pageable pageable = PageableResolver.resolve(page, size, all, ALLOWED_PAGE_SIZE);
        // REQ-SCD-007: fromDate/toDate 둘 다 선택값이라 기본값을 주입하지 않는다 — 둘 다 있을 때만 검증
        if (fromDate != null && toDate != null) {
            if (fromDate.isAfter(toDate)) {
                throw new BusinessException(CommonErrorCode.INVALID_DATE_RANGE);
            }
            if (fromDate.plusDays(MAX_RANGE_DAYS).isBefore(toDate)) {
                throw new BusinessException(CommonErrorCode.DATE_RANGE_EXCEEDED);
            }
        }

        Page<ScheduledTransfer> result =
                scheduledTransferQueryPort.search(customerId, status, withdrawalAccountId, fromDate, toDate, pageable);

        // all=true는 REQ-SCD-007상 조회기간이 선택값이라 기간 상한을 강제할 수 없다 — 대신 결과 건수로
        // 상한을 두고, 조용히 자르지 않고 명시적으로 거부한다(#297 리뷰, danhandev)
        if (all && result.getTotalElements() > MAX_ALL_QUERY_SIZE) {
            throw new BusinessException(CommonErrorCode.ALL_QUERY_TOO_LARGE);
        }

        // 페이지 내 출금계좌번호·별칭을 한 번에 조회 — 원소마다 개별 조회하면 size만큼 N+1이 발생한다
        List<Long> withdrawalAccountIds = result.getContent().stream()
                .map(ScheduledTransfer::getWithdrawalAccountId)
                .distinct()
                .toList();
        Map<Long, String> withdrawalAccountNumbers = accountStatusPort.findAccountNumbersByIds(withdrawalAccountIds);
        Map<Long, String> withdrawalAccountAliases = accountStatusPort.findAccountAliasesByIds(withdrawalAccountIds);

        LocalDate today = LocalDate.now(clock);
        return result.map(scheduledTransfer ->
                toItem(scheduledTransfer, withdrawalAccountNumbers, withdrawalAccountAliases, today));
    }

    @Override
    public ScheduledTransferExecutionResultPage searchExecutionResults(
            Long customerId,
            Long withdrawalAccountId,
            LocalDate fromDate,
            LocalDate toDate,
            ScheduledTransferExecutionResultSort sort,
            int page,
            int size,
            boolean all) {
        if (customerId == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
        Pageable pageable = PageableResolver.resolve(page, size, all, ALLOWED_PAGE_SIZE);
        // REQ-SCD-014: 조회기간 기본값 1개월 — toDate 없으면 오늘, fromDate 없으면 toDate-1개월
        LocalDate today = LocalDate.now(clock);
        LocalDate resolvedToDate = toDate != null ? toDate : today;
        LocalDate resolvedFromDate = fromDate != null ? fromDate : resolvedToDate.minusMonths(DEFAULT_PERIOD_MONTHS);
        if (resolvedFromDate.isAfter(resolvedToDate)) {
            throw new BusinessException(CommonErrorCode.INVALID_DATE_RANGE);
        }
        if (resolvedFromDate.plusDays(MAX_RANGE_DAYS).isBefore(resolvedToDate)) {
            throw new BusinessException(CommonErrorCode.DATE_RANGE_EXCEEDED);
        }

        Page<ScheduledTransfer> result = scheduledTransferQueryPort.searchExecutionResults(
                customerId, withdrawalAccountId, resolvedFromDate, resolvedToDate, sort, pageable);
        ScheduledTransferExecutionResultAggregate aggregate = scheduledTransferQueryPort.summarizeExecutionResults(
                customerId, withdrawalAccountId, resolvedFromDate, resolvedToDate);

        List<Long> withdrawalAccountIds = result.getContent().stream()
                .map(ScheduledTransfer::getWithdrawalAccountId)
                .distinct()
                .toList();
        Map<Long, String> withdrawalAccountNumbers = accountStatusPort.findAccountNumbersByIds(withdrawalAccountIds);

        Page<ScheduledTransferExecutionResultItem> itemPage =
                result.map(scheduledTransfer -> toResultItem(scheduledTransfer, withdrawalAccountNumbers));
        return new ScheduledTransferExecutionResultPage(itemPage, toSummary(aggregate));
    }

    private ScheduledTransferListItem toItem(
            ScheduledTransfer scheduledTransfer,
            Map<Long, String> withdrawalAccountNumbers,
            Map<Long, String> withdrawalAccountAliases,
            LocalDate today) {
        String withdrawalAccountNumber = withdrawalAccountNumbers.get(scheduledTransfer.getWithdrawalAccountId());

        if (withdrawalAccountNumber == null) {
            throw new IllegalStateException("출금계좌 정보를 확인할 수 없습니다.");
        }
        // 별칭은 미설정일 수 있어 Map에 키 자체가 없을 수 있음 - null 허용
        String fromAlias = withdrawalAccountAliases.get(scheduledTransfer.getWithdrawalAccountId());
        // ScheduledTransfer.cancel()과 동일한 조건이어야 한다 - WAITING이어도 실행 예정일 당일이면
        // SCD0303으로 취소가 거부되므로(도메인 규칙), 목록의 cancelable도 같은 기준으로 계산한다(PR #227 리뷰, vsopsw)
        boolean cancelable = scheduledTransfer.getStatus() == ScheduledTransferStatus.WAITING
                && scheduledTransfer.getScheduledDate().isAfter(today);
        return new ScheduledTransferListItem(
                scheduledTransfer.getScheduledTransferId(),
                scheduledTransfer.getWithdrawalAccountId(),
                scheduledTransfer.getScheduledDate(),
                withdrawalAccountNumber,
                fromAlias,
                scheduledTransfer.getPayeeBankCode(),
                scheduledTransfer.getPayeeAccountNumber(),
                scheduledTransfer.getPayeeName(),
                scheduledTransfer.getAmount(),
                scheduledTransfer.getMyPassbookMemo(),
                scheduledTransfer.getStatus(),
                cancelable,
                scheduledTransfer.getRegisteredAt());
    }

    private ScheduledTransferExecutionResultItem toResultItem(
            ScheduledTransfer scheduledTransfer, Map<Long, String> withdrawalAccountNumbers) {
        String withdrawalAccountNumber = withdrawalAccountNumbers.get(scheduledTransfer.getWithdrawalAccountId());

        if (withdrawalAccountNumber == null) {
            throw new IllegalStateException("출금계좌 정보를 확인할 수 없습니다.");
        }
        return new ScheduledTransferExecutionResultItem(
                scheduledTransfer.getScheduledTransferId(),
                scheduledTransfer.getStatus(),
                scheduledTransfer.getExecutedAt(),
                scheduledTransfer.getCanceledAt(),
                withdrawalAccountNumber,
                scheduledTransfer.getPayeeAccountNumber(),
                scheduledTransfer.getPayeeName(),
                scheduledTransfer.getAmount(),
                scheduledTransfer.getTransactionNumber(),
                scheduledTransfer.getFailureReason());
    }

    private ScheduledTransferExecutionResultSummary toSummary(ScheduledTransferExecutionResultAggregate aggregate) {
        return new ScheduledTransferExecutionResultSummary(
                aggregate.successCount(), aggregate.successAmount(),
                aggregate.failedCount(), aggregate.failedAmount(),
                aggregate.canceledCount(), aggregate.canceledAmount());
    }
}
