package com.shinhan.corebank.otp.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.otp.application.port.in.IssueOtpCommand;
import com.shinhan.corebank.otp.application.port.in.IssueOtpResult;
import com.shinhan.corebank.otp.application.port.in.IssueOtpUseCase;
import com.shinhan.corebank.otp.application.port.out.OtpIssueLockPort;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.LockSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// 고객별 발급 잠금을 획득한 뒤 신규 Mock OTP 발급을 위임한다.
@Service
@RequiredArgsConstructor
@Slf4j
public class OtpIssueService implements IssueOtpUseCase {

    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final Duration LOCK_WAIT_TIMEOUT = Duration.ofSeconds(5);
    private static final long INITIAL_RETRY_INTERVAL_NANOS =
            Duration.ofMillis(10).toNanos();
    private static final long MAX_RETRY_INTERVAL_NANOS = Duration.ofMillis(200).toNanos();

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
        long retryIntervalNanos = INITIAL_RETRY_INTERVAL_NANOS;
        do {
            Optional<String> ownerId = issueLockPort.tryAcquire(customerId, LOCK_TTL);
            if (ownerId.isPresent()) {
                return ownerId.get();
            }

            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                break;
            }
            // 동일 고객의 동시 요청이 Redis를 고정 간격으로 두드리지 않도록
            // 최대 200ms의 지수 backoff와 jitter를 적용한다.
            long waitNanos = Math.min(remainingNanos, jitteredWaitNanos(retryIntervalNanos));
            LockSupport.parkNanos(waitNanos);
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
                throw new BusinessException(CommonErrorCode.CONCURRENT_MODIFICATION);
            }
            retryIntervalNanos = nextRetryIntervalNanos(retryIntervalNanos);
        } while (System.nanoTime() < deadline);

        throw new BusinessException(CommonErrorCode.CONCURRENT_MODIFICATION);
    }

    static long nextRetryIntervalNanos(long currentIntervalNanos) {
        return Math.min(currentIntervalNanos * 2, MAX_RETRY_INTERVAL_NANOS);
    }

    private long jitteredWaitNanos(long retryIntervalNanos) {
        long lowerBound = Math.max(INITIAL_RETRY_INTERVAL_NANOS, retryIntervalNanos - retryIntervalNanos / 4);
        if (lowerBound == retryIntervalNanos) {
            return retryIntervalNanos;
        }
        return ThreadLocalRandom.current().nextLong(lowerBound, retryIntervalNanos + 1);
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
