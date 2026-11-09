package com.shinhan.corebank.auth.application.service;

import com.shinhan.corebank.auth.application.port.in.UnregisteredLoginFailureCleanupUseCase;
import com.shinhan.corebank.auth.application.port.out.UnregisteredLoginFailurePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 한 번에 다 지우면 긴 트랜잭션이 되어 로그인 실패 upsert를 오래 막으므로 1,000건씩 나눠 지운다.
@Service
@RequiredArgsConstructor
public class UnregisteredLoginFailureCleanupService implements UnregisteredLoginFailureCleanupUseCase {

    private static final int BATCH_SIZE = 1000;

    private final UnregisteredLoginFailurePort unregisteredLoginFailurePort;

    @Override
    public int deleteExpired() {
        int totalDeleted = 0;
        int deletedThisRound;
        do {
            deletedThisRound = unregisteredLoginFailurePort.deleteExpired(BATCH_SIZE);
            totalDeleted += deletedThisRound;
        } while (deletedThisRound == BATCH_SIZE);
        return totalDeleted;
    }
}
