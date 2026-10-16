package com.shinhan.corebank.account.adapter.in.transfer;

import com.shinhan.corebank.account.application.port.in.AvailableBalanceQueryUseCase;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.limit.domain.exception.LmtErrorCode;
import com.shinhan.corebank.transfer.api.TransferPreCheck;
import com.shinhan.corebank.transfer.api.TransferPreCheckContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AvailableBalancePreCheck implements TransferPreCheck {

    private final AvailableBalanceQueryUseCase availableBalanceQueryUseCase;

    @Override
    public int order() {
        return 100;
    }

    @Override
    public void check(TransferPreCheckContext context) {
        var snapshot = availableBalanceQueryUseCase.getForTransfer(context.withdrawalAccountId(), context.customerId());

        // 계좌 상태 위반은 기존 이체 검증에 맡긴다.
        if (!snapshot.active()) {
            return;
        }

        if (snapshot.availableBalance() < context.amount()) {
            throw new BusinessException(LmtErrorCode.INSUFFICIENT_WITHDRAWABLE_AMOUNT);
        }
    }
}
