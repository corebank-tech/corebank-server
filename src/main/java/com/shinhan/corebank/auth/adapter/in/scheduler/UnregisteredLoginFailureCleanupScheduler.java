package com.shinhan.corebank.auth.adapter.in.scheduler;

import com.shinhan.corebank.auth.application.port.in.UnregisteredLoginFailureCleanupUseCase;
import com.shinhan.corebank.batch.api.BatchExecutionLockPort;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 만료된 미가입 아이디 로그인 실패 기록을 지운다. 만료 여부는 조회 쿼리의 expires_at 조건이 판단하므로
// 이 배치가 늦거나 실패해도 만료된 잠금이 다시 살아나지 않는다. 이 배치는 저장 공간을 비우는 용도다.
@Component
@RequiredArgsConstructor
public class UnregisteredLoginFailureCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(UnregisteredLoginFailureCleanupScheduler.class);
    private static final String JOB_NAME = "UNREGISTERED_LOGIN_FAILURE_CLEANUP";

    private final UnregisteredLoginFailureCleanupUseCase unregisteredLoginFailureCleanupUseCase;
    private final BatchExecutionLockPort batchExecutionLockPort;

    // 30일 보존이라 하루 한 번이면 충분하다. RDS 백업(03:00~03:30)·정보계 ETL(03:30)·RDS 점검(월 04:00~04:30)이
    // 끝난 뒤이고, 다른 스케줄러가 쓰지 않는 40분이다.
    @Scheduled(cron = "0 40 4 * * *", zone = "Asia/Seoul")
    public void cleanupExpired() {
        if (!batchExecutionLockPort.tryAcquire(JOB_NAME)) {
            log.warn("이미 실행 중인 정리 배치가 있어 이번 트리거는 건너뜀 - jobName={}", JOB_NAME);
            return;
        }
        try {
            final int totalDeleted = unregisteredLoginFailureCleanupUseCase.deleteExpired();
            if (totalDeleted > 0) {
                log.info("만료된 미가입 아이디 로그인 실패 기록 정리 완료 - deletedCount={}", totalDeleted);
            }
        } finally {
            batchExecutionLockPort.release(JOB_NAME);
        }
    }
}
