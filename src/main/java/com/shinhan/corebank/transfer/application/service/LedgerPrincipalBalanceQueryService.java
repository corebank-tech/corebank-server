package com.shinhan.corebank.transfer.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.transfer.api.LedgerPrincipalBalanceQuery;
import com.shinhan.corebank.transfer.application.port.out.LedgerPrincipalBalancePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LedgerPrincipalBalanceQueryService implements LedgerPrincipalBalanceQuery {

    private final LedgerPrincipalBalancePort ledgerPrincipalBalancePort;

    @Override
    public long sumNetAmountExcludingInterest(long accountId) {
        if (accountId <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        return ledgerPrincipalBalancePort.sumNetAmountExcludingInterest(accountId);
    }
}
