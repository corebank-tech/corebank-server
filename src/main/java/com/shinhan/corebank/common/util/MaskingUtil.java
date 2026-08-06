package com.shinhan.corebank.common.util;

public class MaskingUtil {
    public static String maskAccountNumber(String accountNumber) {
        if(accountNumber == null || accountNumber.length() != 12) {
            throw new IllegalArgumentException("계좌번호는 12자리여야 합니다. " + accountNumber);
        }
        return accountNumber.substring(0,3) + "******" + accountNumber.substring(9,12);
    }
    private MaskingUtil() {} // new 만드는거 방지
}
