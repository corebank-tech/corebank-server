package com.shinhan.corebank.common.util;

public class MaskingUtil {
    public static String maskAccountNumber(String accountNumber) {
        if(accountNumber == null || !AccountNumberPolicy.ACCOUNT_NUMBER_PATTERN.matcher(accountNumber).matches()) {
            throw new IllegalArgumentException("계좌번호 형식이 올바르지 않습니다.");
        }
        return accountNumber.substring(0,3) + "******" + accountNumber.substring(9,12);
    }

    // "홍길동" -> "홍*동", "홍길" -> "홍*", 1글자는 마스킹 없이 그대로
    public static String maskName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("이름이 비어 있습니다.");
        }
        if (name.length() == 1) {
            return name;
        }
        if (name.length() == 2) {
            return name.charAt(0) + "*";
        }
        return name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }
    private MaskingUtil() {} // new 만드는거 방지
}
