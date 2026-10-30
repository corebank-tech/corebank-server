package com.shinhan.corebank.common.audit;

import java.util.Arrays;

// 관리자 고객 개인정보 조회 · 변경 감사 이벤트(PH-89). 이 타입으로만 사유(access_reason)를 받으므로
// 일반 이벤트에 사유를 붙이는 코드는 컴파일되지 않고, 사유 누락은 감사 행을 만들 때 막는다(#788 리뷰).
public enum PersonalDataAccessEventType {
    CUSTOMER_SEARCH, // 관리자 고객 검색
    CUSTOMER_DETAIL_VIEW, // 관리자 고객 상세 조회
    ACCOUNT_UNLOCK, // 관리자 계정 잠금 해제
    LOGIN_PASSWORD_RESET_BY_ADMIN, // 관리자 로그인 비밀번호 초기화(잠금 해제 포함, #449)
    CUSTOMER_STATUS_CHANGE; // 관리자 고객 계정 상태 변경(이용정지·해제, #450)

    // DB에 문자열로 저장된 event_type이 이 타입인지. 사본 경로를 정할 때 쓴다
    public static boolean includes(String eventTypeName) {
        return Arrays.stream(values()).anyMatch(type -> type.name().equals(eventTypeName));
    }
}
