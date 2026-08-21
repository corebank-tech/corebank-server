package com.shinhan.corebank.transfer.application.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.transfer.application.port.in.TransferHistoryDetail;
import com.shinhan.corebank.transfer.application.port.in.TransferHistoryItem;
import com.shinhan.corebank.transfer.application.port.in.TransferHistoryPage;
import com.shinhan.corebank.transfer.application.port.in.TransferHistoryQueryUseCase;
import com.shinhan.corebank.transfer.application.port.in.TransferHistorySort;
import com.shinhan.corebank.transfer.application.port.in.TransferHistorySummary;
import com.shinhan.corebank.transfer.application.port.out.AccountLockPort;
import com.shinhan.corebank.transfer.application.port.out.TransferHistoryAggregate;
import com.shinhan.corebank.transfer.application.port.out.TransferHistoryQueryPort;
import com.shinhan.corebank.transfer.application.port.out.TransferLookupPort;
import com.shinhan.corebank.transfer.domain.Transfer;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TransferHistoryQueryService implements TransferHistoryQueryUseCase {

    private static final Set<Integer> ALLOWED_PAGE_SIZE = Set.of(5, 10, 20, 30, 50);
    private static final int MAX_RANGE_DAYS = 365;
    private static final int DEFAULT_PERIOD_MONTHS = 1;

    private final TransferHistoryQueryPort transferHistoryQueryPort;
    private final TransferLookupPort transferLookupPort;
    private final AccountLockPort accountLockPort;
    private final Clock clock;

    public TransferHistoryQueryService(
            TransferHistoryQueryPort transferHistoryQueryPort,
            TransferLookupPort transferLookupPort,
            AccountLockPort accountLockPort,
            Clock clock
    ) {
        this.transferHistoryQueryPort = transferHistoryQueryPort;
        this.transferLookupPort = transferLookupPort;
        this.accountLockPort = accountLockPort;
        this.clock = clock;
    }

    @Override
    public TransferHistoryPage search(Long customerId, Long withdrawalAccountId, ProcessResultStatus status,
                                       LocalDate fromDate, LocalDate toDate, TransferHistorySort sort, int page, int size) {
        if (customerId == null || withdrawalAccountId == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
        if (!ALLOWED_PAGE_SIZE.contains(size)) {
            throw new BusinessException(CommonErrorCode.INVALID_PAGE_SIZE);
        }
        if (page < 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "page는 0 이상이어야 합니다.");
        }
        requireOwnership(customerId, withdrawalAccountId);

        LocalDate today = LocalDate.now(clock);
        LocalDate resolvedToDate = toDate != null ? toDate : today;
        LocalDate resolvedFromDate = fromDate != null ? fromDate : resolvedToDate.minusMonths(DEFAULT_PERIOD_MONTHS);
        if (resolvedFromDate.isAfter(resolvedToDate)) {
            throw new BusinessException(CommonErrorCode.INVALID_DATE_RANGE);
        }
        if (resolvedFromDate.plusDays(MAX_RANGE_DAYS).isBefore(resolvedToDate)) {
            throw new BusinessException(CommonErrorCode.DATE_RANGE_EXCEEDED);
        }

        TransferHistorySort resolvedSort = sort != null ? sort : TransferHistorySort.LATEST;
        Page<Transfer> result = transferHistoryQueryPort.search(
                withdrawalAccountId, status, resolvedFromDate, resolvedToDate, resolvedSort, PageRequest.of(page, size));
        TransferHistoryAggregate aggregate = transferHistoryQueryPort.summarize(
                withdrawalAccountId, status, resolvedFromDate, resolvedToDate);
        OffsetDateTime asOf = OffsetDateTime.ofInstant(clock.instant(), clock.getZone());

        return new TransferHistoryPage(asOf, result.map(this::toItem), toSummary(aggregate));
    }

    @Override
    public TransferHistoryDetail getDetail(Long customerId, String transactionNumber) {
        if (customerId == null || transactionNumber == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }

        Transfer transfer = transferLookupPort.findByTransactionNumber(transactionNumber)
                .filter(t -> isOwnedBy(customerId, t.getWithdrawalAccountId()))
                .orElseThrow(() -> new BusinessException(TransferErrorCode.TRANSACTION_NOT_FOUND));

        return toDetail(transfer);
    }

    private void requireOwnership(Long customerId, Long withdrawalAccountId) {
        if (!isOwnedBy(customerId, withdrawalAccountId)) {
            throw new BusinessException(TransferErrorCode.WITHDRAWAL_ACCOUNT_NOT_REGISTERED);
        }
    }

    // transfer 테이블엔 customer_id가 없어(withdrawal_account_id만 있음) AccountLockPort로
    // 계좌 소유자를 확인한다. TransferExecutionService의 1차 검증과 달리 withdrawalRegistered()는
    // 보지 않는다 — 출금계좌 등록을 해지한 뒤에도 과거 이체 이력은 계속 조회할 수 있어야 한다.
    private boolean isOwnedBy(Long customerId, Long withdrawalAccountId) {
        return accountLockPort.findWithdrawalAccountDetail(withdrawalAccountId)
                .filter(detail -> detail.customerId().equals(customerId))
                .isPresent();
    }

    private TransferHistoryItem toItem(Transfer transfer) {
        return new TransferHistoryItem(
                transfer.getTransactionNumber(),
                transfer.getStatus(),
                transfer.getTransferredAt(),
                transfer.getWithdrawalAccountId(),
                transfer.getDepositAccountNumber(),
                transfer.getPayeeName(),
                transfer.getAmount(),
                transfer.getTransferType(),
                transfer.getChannel(),
                transfer.getErrorCode(),
                transfer.getErrorMessage()
        );
    }

    private TransferHistoryDetail toDetail(Transfer transfer) {
        return new TransferHistoryDetail(
                transfer.getTransactionNumber(),
                transfer.getStatus(),
                transfer.getWithdrawalAccountId(),
                transfer.getDepositAccountId(),
                transfer.getDepositAccountNumber(),
                transfer.getPayeeName(),
                transfer.getAmount(),
                transfer.getFee(),
                transfer.getTransferType(),
                transfer.getChannel(),
                transfer.getMyPassbookMemo(),
                transfer.getRecipientPassbookMemo(),
                transfer.getWithdrawalBalanceAfter(),
                transfer.getErrorCode(),
                transfer.getErrorMessage(),
                transfer.getTransferredAt()
        );
    }

    private TransferHistorySummary toSummary(TransferHistoryAggregate aggregate) {
        return new TransferHistorySummary(
                aggregate.successCount(), aggregate.successAmount(),
                aggregate.errorCount(), aggregate.errorAmount());
    }
}
