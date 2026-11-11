package com.shinhan.corebank.transfer.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.transfer.api.RecipientHistory;
import com.shinhan.corebank.transfer.api.SucceededTransfer;
import com.shinhan.corebank.transfer.api.SucceededTransferHistoryQuery;
import com.shinhan.corebank.transfer.application.port.out.SucceededTransferHistoryPort;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SucceededTransferHistoryService implements SucceededTransferHistoryQuery {

    private final SucceededTransferHistoryPort succeededTransferHistoryPort;

    @Override
    public List<SucceededTransfer> findRecent(
            long customerId, LocalDateTime fromInclusive, LocalDateTime toExclusive, int limit) {
        // 잘못된 범위를 빈 목록으로 돌려주면 호출자 결함이 "이력 없음"으로 보인다
        if (limit <= 0 || !fromInclusive.isBefore(toExclusive)) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }
        return succeededTransferHistoryPort.findRecentSucceeded(customerId, fromInclusive, toExclusive, limit);
    }

    @Override
    public RecipientHistory summarizeRecipient(
            long customerId, String depositAccountNumber, long excludeWithdrawalAccountId) {
        return succeededTransferHistoryPort.summarizeRecipient(
                customerId, depositAccountNumber, excludeWithdrawalAccountId);
    }
}
