package com.shinhan.corebank.transfer.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.transfer.api.CounterpartyTransfer;
import com.shinhan.corebank.transfer.api.CounterpartyTransferQuery;
import com.shinhan.corebank.transfer.application.port.out.CounterpartyTransferQueryPort;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CounterpartyTransferQueryService implements CounterpartyTransferQuery {

    private final CounterpartyTransferQueryPort counterpartyTransferQueryPort;

    @Override
    public List<CounterpartyTransfer> findByAccount(
            long accountId, LocalDateTime fromInclusive, LocalDateTime toExclusive) {
        if (fromInclusive == null || toExclusive == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
        if (!fromInclusive.isBefore(toExclusive)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return counterpartyTransferQueryPort.findByAccount(accountId, fromInclusive, toExclusive);
    }
}
