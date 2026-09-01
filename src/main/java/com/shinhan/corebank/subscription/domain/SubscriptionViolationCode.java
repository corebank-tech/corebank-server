package com.shinhan.corebank.subscription.domain;

/**
 * 사전 검증 violation의 코드/문구 원본. docs/api_conventions.md 마스터 목록과 1:1로 맞춘다.
 * BusinessException으로 던지는 값이 아니라 200 본문에 담기는 값이라 ErrorCode를 구현하지 않는다.
 */
public enum SubscriptionViolationCode {
    PRODUCT_NOT_ON_SALE("PRD0007", "판매중인 상품이 아닙니다."),
    AMOUNT_OUT_OF_RANGE("PRD0001", "가입금액이 상품 한도 범위를 벗어났습니다."),
    AMOUNT_UNIT_MISMATCH("PRD0004", "가입금액이 상품의 입력 단위에 맞지 않습니다."),
    TERM_NOT_ALLOWED("PRD0002", "가입기간이 상품 허용 범위를 벗어났습니다."),
    INSUFFICIENT_BALANCE("LMT0001", "출금가능금액이 부족합니다."),
    REQUIRED_TERMS_NOT_AGREED("PRD0003", "필수 약관에 동의하지 않았습니다."),
    TERMS_NOT_VIEWED("PRD0005", "약관 전문을 확인한 후 동의해 주세요."),
    TERMS_VERSION_MISMATCH("PRD0006", "약관이 변경되었습니다. 다시 확인해 주세요.");

    private final String code;
    private final String message;

    SubscriptionViolationCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
