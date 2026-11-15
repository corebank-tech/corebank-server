package com.shinhan.corebank.paymentstop.adapter.out.account;

import com.shinhan.corebank.account.api.PaymentStopUseCase;
import com.shinhan.corebank.paymentstop.application.port.out.AccountStopPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccountStopAdapter implements AccountStopPort {

    private final PaymentStopUseCase paymentStopUseCase;

    @Override
    public void applyFullStop(long accountId, long caseId) {
        paymentStopUseCase.applyFullStop(accountId, caseId);
    }

    @Override
    public long convertToPartial(long accountId, long caseId, long requestedAmount) {
        return paymentStopUseCase
                .convertToPartial(accountId, caseId, requestedAmount)
                .appliedAmount();
    }

    @Override
    public void release(long accountId, long caseId) {
        paymentStopUseCase.release(accountId, caseId);
    }

    @Override
    public boolean hasOtherStopHistory(long accountId, long excludingCaseId) {
        return paymentStopUseCase.hasOtherStopHistory(accountId, excludingCaseId);
    }
}
