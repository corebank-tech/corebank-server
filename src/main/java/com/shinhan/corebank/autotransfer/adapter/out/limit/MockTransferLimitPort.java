package com.shinhan.corebank.autotransfer.adapter.out.limit;

import com.shinhan.corebank.autotransfer.application.port.out.TransferLimitPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "test", "scratch"})
public class MockTransferLimitPort implements TransferLimitPort {
    @Override
    public long findOneTimeLimit(Long customerId) {
        return 10_000_000L;
    }
}
