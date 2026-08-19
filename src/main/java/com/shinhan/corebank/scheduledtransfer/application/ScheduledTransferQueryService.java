package com.shinhan.corebank.scheduledtransfer.application;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferQueryUseCase;
import com.shinhan.corebank.scheduledtransfer.application.port.out.ScheduledTransferQueryPort;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransfer;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduledTransferQueryService implements ScheduledTransferQueryUseCase {
    private static final Set<Integer> ALLOWED_PAGE_SIZE = Set.of(5, 10, 20, 30, 50);
    private static final int MAX_RANGE_DAYS = 365;

    private final ScheduledTransferQueryPort scheduledTransferQueryPort;

    @Override
    public Page<ScheduledTransfer> search(Long customerId, ScheduledTransferStatus status, Long withdrawalAccountId,
                                          LocalDate fromDate, LocalDate toDate, int page, int size) {
        if (customerId == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
        if (!ALLOWED_PAGE_SIZE.contains(size)) {
            throw new BusinessException(CommonErrorCode.INVALID_PAGE_SIZE);
        }
        if (page < 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "page는 0 이상이어야 합니다.");
        }
        // REQ-SCD-007: startDate/endDate 둘 다 선택값이라 기본값을 주입하지 않는다 — 둘 다 있을 때만 검증
        if (fromDate != null && toDate != null) {
            if (fromDate.isAfter(toDate)) {
                throw new BusinessException(CommonErrorCode.INVALID_DATE_RANGE);
            }
            if (fromDate.plusDays(MAX_RANGE_DAYS).isBefore(toDate)) {
                throw new BusinessException(CommonErrorCode.DATE_RANGE_EXCEEDED);
            }
        }

        return scheduledTransferQueryPort.search(customerId, status, withdrawalAccountId, fromDate, toDate,
                PageRequest.of(page, size));
    }
}
