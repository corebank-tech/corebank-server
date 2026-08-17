package com.shinhan.corebank.transfer.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.transfer.application.port.in.PayeeInquiryResult;
import com.shinhan.corebank.transfer.application.port.in.PayeeInquiryUseCase;
import com.shinhan.corebank.transfer.application.port.out.AccountLockPort;
import com.shinhan.corebank.transfer.application.port.out.LockedAccountStatus;
import com.shinhan.corebank.transfer.application.port.out.ResolvedPayee;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PayeeInquiryService implements PayeeInquiryUseCase {

    private final AccountLockPort accountLockPort;

    public PayeeInquiryService(AccountLockPort accountLockPort) {
        this.accountLockPort = accountLockPort;
    }

    @Override
    public PayeeInquiryResult inquire(String depositAccountNumber) {
        ResolvedPayee payee = accountLockPort.resolvePayeeByAccountNumber(depositAccountNumber)
                .orElseThrow(() -> new BusinessException(TransferErrorCode.PAYEE_NOT_FOUND));

        if (payee.status() != LockedAccountStatus.ACTIVE) {
            throw new BusinessException(TransferErrorCode.PAYEE_ACCOUNT_SUSPENDED);
        }

        return PayeeInquiryResult.builder()
                .accountId(payee.accountId())
                .payeeName(payee.payeeName())
                .build();
    }
}
