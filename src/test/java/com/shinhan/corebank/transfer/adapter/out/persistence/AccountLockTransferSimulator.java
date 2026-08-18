package com.shinhan.corebank.transfer.adapter.out.persistence;

import java.time.LocalDateTime;

import com.shinhan.corebank.transfer.application.port.out.AccountLockPort;
import com.shinhan.corebank.transfer.application.port.out.LockedAccountsForTransfer;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 테스트 전용 헬퍼. Day4(TransferExecutionService)가 구현할 [락 획득 → 출금 → 입금]
 * 순서를 하나의 트랜잭션으로 재현해, AccountLockPort의 데드락 회피 동작을
 * 실제 서비스 트랜잭션 경계 안에서 검증할 수 있게 한다.
 */
@Component
class AccountLockTransferSimulator {

    private final AccountLockPort accountLockPort;

    AccountLockTransferSimulator(AccountLockPort accountLockPort) {
        this.accountLockPort = accountLockPort;
    }

    @Transactional
    void execute(Long withdrawalAccountId, Long depositAccountId, long amount) {
        LockedAccountsForTransfer locked = accountLockPort.lockForTransfer(withdrawalAccountId, depositAccountId);
        accountLockPort.applyTransfer(locked, amount, LocalDateTime.now());
    }
}
