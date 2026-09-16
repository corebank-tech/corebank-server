package com.shinhan.corebank.transfer.adapter.out.limit;

import com.shinhan.corebank.limit.api.TransferLimitReserver;
import com.shinhan.corebank.transfer.application.port.out.TransferLimitPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 빈 이름 명시: autotransfer/scheduledtransfer의 TransferLimitAdapter와 클래스 단순이름이 같아
// 기본 빈 이름(transferLimitAdapter)이 충돌한다.
@Component("transferTransferLimitAdapter")
@RequiredArgsConstructor
public class TransferLimitAdapter implements TransferLimitPort {

    private final TransferLimitReserver transferLimitReserver;

    @Override
    public void checkAndReserve(Long customerId, long amount) {
        transferLimitReserver.checkAndReserve(customerId, amount);
    }
}
