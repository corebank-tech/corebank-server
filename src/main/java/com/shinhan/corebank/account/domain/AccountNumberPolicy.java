package com.shinhan.corebank.account.domain;

//계좌번호 생성과 관련한 변하지 않는 규칙 모아둠
public class AccountNumberPolicy {

    public static final String BANK_CODE = "088";

    public static final int BANK_CODE_LENGTH = 3;
    public static final int PRODUCT_PREFIX_LENGTH = 2;
    public static final int SEQUENCE_LENGTH = 7;
    public static final int ACCOUNT_NUMBER_LENGTH = 12;

    public static final long MAX_SEQUENCE = 9_999_999L;

    private AccountNumberPolicy() {
        // 상수만 제공하는 클래스이므로 객체 생성을 막는다.
    }
}
