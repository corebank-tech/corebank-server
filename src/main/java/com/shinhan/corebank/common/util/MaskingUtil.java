package com.shinhan.corebank.common.util;

public class MaskingUtil {
    public static String maskAccountNumber(String accountNumber) {
        if(accountNumber == null || !AccountNumberPolicy.ACCOUNT_NUMBER_PATTERN.matcher(accountNumber).matches()) {
            throw new IllegalArgumentException("계좌번호 형식이 올바르지 않습니다.");
        }
        return accountNumber.substring(0,3) + "******" + accountNumber.substring(9,12);
    }
    private MaskingUtil() {} // new 만드는거 방지
}
