package com.shinhan.corebank.scheduledtransfer.adapter.out.limit;

import com.shinhan.corebank.scheduledtransfer.application.port.out.TransferLimitPort;
import org.springframework.stereotype.Component;

// 이 포트의 다른 구현체가 없어 @Profile로 좁히면 prod 기동이 실패한다 - 실구현 전까지 모든 프로필에서 활성화한다.
// 빈 이름 명시: autotransfer.adapter.out.limit.MockTransferLimitPort와 클래스 단순이름이 같아
// 기본 빈 이름(mockTransferLimitPort)이 충돌한다.
@Component("scheduledTransferMockTransferLimitPort")
public class MockTransferLimitPort implements TransferLimitPort {
    @Override
    public long findOneTimeLimit(Long customerId) {
        return 10_000_000L;
    }
}
