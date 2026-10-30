package com.shinhan.corebank.transfer.adapter.out.external;

import java.util.Arrays;

/**
 * 응답코드 (규격 §3). 이 프로젝트가 정한 값이고 실제 기관 코드표가 아니다.
 *
 * 처리 불명(TIMEOUT)은 응답이 오지 않은 상태라 여기 없다.
 */
public enum ResponseCode {
    APPROVED("0000"),
    ACCOUNT_NOT_FOUND("1001"),
    ACCOUNT_UNAVAILABLE("1002"),
    MALFORMED_MESSAGE("1003"),
    DUPLICATE("2001"),
    ORIGINAL_NOT_RECEIVED("3001"),
    SYSTEM_ERROR("9999");

    private final String code;

    ResponseCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    static ResponseCode fromCode(String code) {
        return Arrays.stream(values())
                .filter(responseCode -> responseCode.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 응답코드다"));
    }
}
