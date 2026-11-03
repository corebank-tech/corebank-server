package com.shinhan.corebank.transfer.domain;

/** 이체 보류 상태. 전이는 조건부 UPDATE로만 한다(transfer_seam.md §9-4). */
public enum TransferHoldStatus {
    // 보류 중. 실행·거절·취소·만료를 기다린다
    HELD,

    // 실행을 선점했다. 이체 결과를 기다린다
    EXECUTING,

    // 실행해서 이체가 성공했다
    EXECUTED,

    // 실행했지만 실행 시점 검사에서 거부됐다
    FAILED,

    // 관리자가 거절했다
    REJECTED,

    // 고객이 취소했다
    CANCELLED,

    // 승인 기한이 지났다
    EXPIRED
}
