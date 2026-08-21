package com.shinhan.corebank.scheduledtransfer.adapter.out.limit;

import com.shinhan.corebank.scheduledtransfer.application.port.out.TransferLimitPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// 빈 이름 명시: autotransfer.adapter.out.limit.MockTransferLimitPort와 클래스 단순이름이 같아
// 기본 빈 이름(mockTransferLimitPort)이 충돌한다.
@Component("scheduledTransferMockTransferLimitPort")
@Profile({"local", "test", "scratch"})
public class MockTransferLimitPort implements TransferLimitPort {
    @Override
    public long findOneTimeLimit(Long customerId) {
        return 10_000_000L;
    }
}
