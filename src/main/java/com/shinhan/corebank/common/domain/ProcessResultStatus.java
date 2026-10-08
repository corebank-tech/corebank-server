package com.shinhan.corebank.common.domain;

public enum ProcessResultStatus {
    SUCCESS, // 성공
    ERROR, // 실패
    PROCESSING, // 처리중. 응답 유실·타임아웃으로 결과 미확정 시에만 사용
    TIMEOUT; // 처리 불명. 대외 전문 무응답으로 상대 처리 여부를 모른다. 조회거래로 확정한다(PH-80)

    /** 확정 상태 여부. 처리중 건 재확정 배치에서 사용 */
    public boolean isConfirmed() {
        // 새 값이 생겨도 기본이 미확정이 되도록 긍정형으로 둔다 — 결과를 모르는 거래를 ERROR로 굳히지 않는다
        return this == SUCCESS || this == ERROR;
    }
}
