package com.shinhan.corebank.autotransfer.application.service;

import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferListItem;
import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferQueryUseCase;
import com.shinhan.corebank.autotransfer.application.port.out.AccountStatusPort;
import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferQueryPort;
import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.common.util.PageableResolver;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AutoTransferQueryService implements AutoTransferQueryUseCase {
    private final AutoTransferQueryPort autoTransferQueryPort;
    private final AccountStatusPort accountStatusPort;
    private final Clock clock;

    @Override
    public Page<AutoTransferListItem> search(
            Long customerId, Long withdrawalAccountId, AutoTransferStatus status, int page, int size, boolean all) {
        if (customerId == null || withdrawalAccountId == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
        Pageable pageable = PageableResolver.resolve(page, size, all, PageableResolver.ALLOWED_PAGE_SIZES);
        // withdrawalAccountId가 필수 파라미터라 페이지 안 모든 행이 같은 계좌 - 별칭은 요청당 1회만 조회
        String fromAlias =
                accountStatusPort.findAccountAlias(withdrawalAccountId).orElse(null);
        LocalDate today = LocalDate.now(clock);
        return autoTransferQueryPort
                .search(customerId, withdrawalAccountId, status, pageable)
                .map(autoTransfer -> toItem(autoTransfer, fromAlias, today));
    }

    private AutoTransferListItem toItem(AutoTransfer autoTransfer, String fromAlias, LocalDate today) {
        boolean cancelable = autoTransfer.getStatus().isModifiable()
                && !autoTransfer.getNextExecutionDate().equals(today);
        return new AutoTransferListItem(
                autoTransfer.getAutoTransferId(),
                autoTransfer.getDepositAccountNumber(),
                fromAlias,
                autoTransfer.getPayeeName(),
                autoTransfer.getAmount(),
                autoTransfer.getStartDate(),
                autoTransfer.getEndDate(),
                autoTransfer.getTransferDay(),
                autoTransfer.getCycleMonths(),
                autoTransfer.getMyPassbookMemo(),
                autoTransfer.getStatus(),
                cancelable,
                autoTransfer.getRegisteredAt());
    }
}
