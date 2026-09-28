package com.shinhan.corebank.batch.api;

public interface BatchExecutionLockPort {
    // 이미 실행 중 - false, 아니면 currently_running을 true로 바꾸고 true 반환
    boolean tryAcquire(String jobName);
    // currently_running을 false로 되돌림 -> 배치가 성공하든 실패하든 항상 호출
    void release(String jobName);
    // 락을 잡지 않고 currently_running만 조회한다. 다른 job이 실행 중인지 확인만 하고 싶을 때
    // 쓴다(예: #378 대사 배치가 DAILY_TRANSFER_BATCH 완료를 기다릴 때, PR #463 리뷰).
    // 존재하지 않는 jobName은 실행 중이 아닌 것으로 본다(false).
    boolean isRunning(String jobName);
}
