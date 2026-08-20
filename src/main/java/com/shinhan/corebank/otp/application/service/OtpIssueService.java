package com.shinhan.corebank.otp.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.otp.application.port.in.IssueOtpCommand;
import com.shinhan.corebank.otp.application.port.in.IssueOtpResult;
import com.shinhan.corebank.otp.application.port.in.IssueOtpUseCase;
import com.shinhan.corebank.otp.application.port.out.OtpIssueLockPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.locks.LockSupport;

// 고객별 발급 잠금을 획득한 뒤 신규 Mock OTP 발급을 위임한다.
@Service
@RequiredArgsConstructor
@Slf4j
public class OtpIssueService implements IssueOtpUseCase {

    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final Duration LOCK_WAIT_TIMEOUT = Duration.ofSeconds(5);
    private static final long RETRY_INTERVAL_NANOS = Duration.ofMillis(10).toNanos();

    private final OtpIssueLockPort issueLockPort;
    private final OtpIssueProcessor processor;
    private final OtpTransactionDataValidator transactionDataValidator;

    @Override
    public IssueOtpResult issue(IssueOtpCommand command) {
        if (command == null || command.customerId() == null || command.transactionType() == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        transactionDataValidator.validate(command.transactionData());

        String ownerId = acquireLock(command.customerId());
        try {
            // 별도 Bean의 트랜잭션이 커밋된 뒤에만 분산 잠금을 해제한다.
            return processor.issue(command);
        } finally {
            releaseLock(command.customerId(), ownerId);
        }
    }

    private String acquireLock(Long customerId) {
        long deadline = System.nanoTime() + LOCK_WAIT_TIMEOUT.toNanos();
        do {
            Optional<String> ownerId = issueLockPort.tryAcquire(customerId, LOCK_TTL);
            if (ownerId.isPresent()) {
                return ownerId.get();
            }
            LockSupport.parkNanos(RETRY_INTERVAL_NANOS);
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
                throw new BusinessException(CommonErrorCode.CONCURRENT_MODIFICATION);
            }
        } while (System.nanoTime() < deadline);

        throw new BusinessException(CommonErrorCode.CONCURRENT_MODIFICATION);
    }

    private void releaseLock(Long customerId, String ownerId) {
        try {
            issueLockPort.release(customerId, ownerId);
        } catch (RuntimeException exception) {
            // 커밋된 OTP 발급을 실패로 바꾸지 않고 잠금 TTL 만료로 복구한다.
            log.error("OTP 발급 잠금 해제에 실패했습니다. customerId={}", customerId, exception);
        }
    }
}
