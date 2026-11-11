package com.shinhan.corebank.paymentstop.domain;

/**
 * 이의제기 진행 상태(PA-10). 심사 기한은 소명 완비 확인(UNDER_REVIEW 진입)부터 센다(D12).
 *
 * <p>막는 쪽(불수용, 보완 요청, 외부 확인)은 확정 전 언제나 열려 있고, 보류를 줄이는 판정은 소명 완비 확인 뒤에만 쓸 수 있다(D9).
 */
public enum ObjectionStatus {
    // 접수. 소명 완비 확인 전
    RECEIVED,
    // 소명 완비 확인됨. 심사 기한 진행 중
    UNDER_REVIEW,
    // 보완 요청됨. 보완 기한 진행 중
    SUPPLEMENT_REQUESTED,
    // 명의인이 보완 자료를 냄. 담당자가 다시 완비를 확인해야 한다
    SUPPLEMENT_SUBMITTED,
    // 수용 또는 일부 지급정지 판정이 작성돼 승인 대기
    PENDING_APPROVAL,
    // 심사결과 확정
    DECIDED
}
