package com.shinhan.corebank.common.util;

import java.util.regex.Pattern;

public class AccountNumberPolicy {
    public static final Pattern ACCOUNT_NUMBER_PATTERN = Pattern.compile("^[0-9]{12}$");
    // 타행 계좌번호. 은행마다 자릿수가 달라 전문 계좌번호 칸(16바이트)까지만 받는다(fixed_length_message_spec.md §2).
    public static final Pattern INTERBANK_ACCOUNT_NUMBER_PATTERN = Pattern.compile("^[0-9]{1,16}$");

    private AccountNumberPolicy() {}
}
