package com.shinhan.corebank.common.domain;

public enum ProcessResultStatus {
    SUCCESS, // 성공
    ERROR, // 실패
    PROCESSING; // 처리중. 응답 유실·타임아웃으로 결과 미확정 시에만 사용

    /** 확정 상태 여부. 처리중 건 재확정 배치에서 사용 */
    public boolean isConfirmed() {
        return this != PROCESSING;
    }
}
