package com.shinhan.corebank.common.util;

import java.util.regex.Pattern;

// 예외 메시지에 마스킹 대상 원문을 절대 포함하지 말 것 — 로그로 그대로 유출됨(#42 참고)
public class MaskingUtil {
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\d{11}$");

    public static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null
                || !AccountNumberPolicy.ACCOUNT_NUMBER_PATTERN
                        .matcher(accountNumber)
                        .matches()) {
            throw new IllegalArgumentException("계좌번호 형식이 올바르지 않습니다.");
        }
        return accountNumber.substring(0, 3) + "******" + accountNumber.substring(9, 12);
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

    // "abcdef@test.com" -> "abcd**@test.com", 로컬파트 4자 이하는 마지막 1자만 마스킹
    public static String maskEmail(String email) {
        if (email == null) {
            throw new IllegalArgumentException("이메일이 비어있습니다.");
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0 || atIndex == email.length() - 1) {
            throw new IllegalArgumentException("이메일 형식이 올바르지 않습니다.");
        }

        String localPart = email.substring(0, atIndex);
        String domainPart = email.substring(atIndex);
        int visibleLength = localPart.length() <= 4 ? localPart.length() - 1 : 4;

        return localPart.substring(0, visibleLength) + "*".repeat(localPart.length() - visibleLength) + domainPart;
    }

    // "01012345678" -> "010****5678"
    public static String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || !PHONE_PATTERN.matcher(phoneNumber).matches()) {
            throw new IllegalArgumentException("휴대폰 번호 형식이 올바르지 않습니다.");
        }
        return phoneNumber.substring(0, 3) + "****" + phoneNumber.substring(7);
    }

    private MaskingUtil() {} // new 만드는거 방지
}
