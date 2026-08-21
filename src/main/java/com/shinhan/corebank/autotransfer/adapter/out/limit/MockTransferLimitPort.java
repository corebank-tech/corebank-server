package com.shinhan.corebank.autotransfer.adapter.out.limit;

import com.shinhan.corebank.autotransfer.application.port.out.TransferLimitPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// 이 포트의 다른 구현체가 없어 @Profile로 좁히면 prod 기동이 실패한다 - 실구현 전까지 모든 프로필에서 활성화한다.
@Component
@Profile({"local", "test", "scratch"})
public class MockTransferLimitPort implements TransferLimitPort {
    @Override
    public long findOneTimeLimit(Long customerId) {
        return 10_000_000L;
    }
}
