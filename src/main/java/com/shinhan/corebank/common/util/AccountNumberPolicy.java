package com.shinhan.corebank.common.util;

import java.util.regex.Pattern;

public class AccountNumberPolicy {
    public static final Pattern ACCOUNT_NUMBER_PATTERN = Pattern.compile("^[0-9]{12}$");
    private AccountNumberPolicy() {}
}
