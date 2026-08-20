package com.shinhan.corebank.common.audit;

/**
 * 감사 이벤트 종류 (REQ-NFR-010).
 * 원장에 영향을 준 행위와 인증 수단 변경 행위를 구분한다.
 */
public enum AuditEventType {

    // 원장 변경 — transaction_number 가 반드시 채워진다
    IMMEDIATE_TRANSFER,          // 즉시이체 실행
    SCHEDULED_TRANSFER,          // 예약이체 배치 실행
    AUTO_TRANSFER,               // 자동이체 배치 실행
    PRODUCT_SUBSCRIPTION,        // 상품가입 (초입금 발생)

    // 원장 미변경 — transaction_number 는 NULL
    TRANSFER_LIMIT_CHANGE,       // 이체한도 변경
    ACCOUNT_PASSWORD_CHANGE,     // 계좌비밀번호 변경
    AUTO_TRANSFER_INFO_CHANGE,   // 자동이체 등록 정보 변경(REQ-AUTO-010)
    SCHEDULED_TRANSFER_INFO_CHANGE, // 예약이체 등록 정보 변경(REQ-SCD 등록/취소)
    WITHDRAWAL_ACCOUNT_REGISTER, // 출금계좌 등록
    WITHDRAWAL_ACCOUNT_DELETE,   // 출금계좌 삭제
    LOGIN,                       // 로그인 (REQ-CMN-025 접속이력)
    ACCOUNT_UNLOCK;              // 관리자 계정 잠금 해제

    /** 원장 변경 여부. true 면 transaction_number 가 있어야 한다 */
    public boolean isLedgerChanging() {
        return this == IMMEDIATE_TRANSFER
                || this == SCHEDULED_TRANSFER
                || this == AUTO_TRANSFER
                || this == PRODUCT_SUBSCRIPTION;
    }
}