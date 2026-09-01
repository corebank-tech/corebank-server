package com.shinhan.corebank.batch.adapter.out.persistence;

import com.shinhan.corebank.batch.application.port.out.BatchExecutionLockPort;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class BatchExecutionLockPersistenceAdapter implements BatchExecutionLockPort {
    private static final Logger log = LoggerFactory.getLogger(BatchExecutionLockPersistenceAdapter.class);
    // 6시간 넘게 실행중으로 남아있으면 강제로 재획득을 허용
    private static final Duration STALE_THRESHOLD = Duration.ofHours(6);
    private final BatchExecutionLockJpaRepository batchExecutionLockJpaRepository;
    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryAcquire(String jobName) {
        BatchExecutionLockJpaEntity lock = batchExecutionLockJpaRepository
                .findByJobNameForUpdate(jobName)
                .orElseThrow(() -> new IllegalStateException("배치 락 행이 없습니다 - jobName = " + jobName));

        LocalDateTime now = LocalDateTime.now(clock);
        if (lock.isCurrentlyRunning()) {
            if (!lock.isStale(now, STALE_THRESHOLD)) {
                return false;
            }
            log.warn(
                    "이전 배치가 {} 넘게 실행 중으로 남아있어(크래시로 release() 누락 추정) 강제로 재획득함 - jobName={}, lastUpdatedAt={}",
                    STALE_THRESHOLD,
                    jobName,
                    lock.getUpdatedAt());
        }
        lock.markRunning(now);
        return true;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(String jobName) {
        batchExecutionLockJpaRepository
                .findByJobNameForUpdate(jobName)
                .ifPresent(lock -> lock.markIdle(LocalDateTime.now(clock)));
    }
}
