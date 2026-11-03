package com.shinhan.corebank.common.audit;

/** 관리자가 고객 개인정보를 조회·변경하는 사유(D-10). 헤더로 받아 감사의 Why에 남긴다. */
public enum AccessReason {
    CUSTOMER_REQUEST, // 고객 요청
    INCIDENT, // 사고·민원 대응
    AUDIT, // 감사·점검
    OPERATION; // 운영 확인

    public static final String HEADER = "X-Access-Reason";
}
