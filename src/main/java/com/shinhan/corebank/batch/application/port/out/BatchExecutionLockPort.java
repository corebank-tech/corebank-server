package com.shinhan.corebank.batch.application.port.out;

public interface BatchExecutionLockPort {
    // 이미 실행 중 - false, 아니면 currently_running을 true로 바꾸고 true 반환
    boolean tryAcquire(String jobName);
    // currently_running을 false로 되돌림 -> 배치가 성공하든 실패하든 항상 호출
    void release(String jobName);
}
