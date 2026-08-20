package com.shinhan.corebank.signup.application.port.in;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;

import java.util.regex.Pattern;

// 회원가입 실명·계좌 인증에 필요한 검증 입력을 전달한다.
public record VerifySignupAccountCommand(
        String userName,
        String birthDate,
        String accountNumber,
        String accountPassword
) {

    private static final int MAX_USER_NAME_LENGTH = 50;
    private static final Pattern BIRTH_DATE_PATTERN =
            Pattern.compile("^\\d{6}$");
    private static final Pattern ACCOUNT_NUMBER_PATTERN =
            Pattern.compile("^\\d{12}$");
    private static final Pattern ACCOUNT_PASSWORD_PATTERN =
            Pattern.compile("^\\d{4}$");

    public VerifySignupAccountCommand {
        if (userName == null || userName.isBlank()) {
            throw invalidInput();
        }

        userName = userName.trim();
        if (userName.length() > MAX_USER_NAME_LENGTH) {
            throw invalidInput();
        }

        if (!matches(BIRTH_DATE_PATTERN, birthDate)
                || !matches(ACCOUNT_NUMBER_PATTERN, accountNumber)
                || !matches(ACCOUNT_PASSWORD_PATTERN, accountPassword)) {
            throw invalidInput();
        }
    }

    private static boolean matches(Pattern pattern, String value) {
        return value != null && pattern.matcher(value).matches();
    }

    private static BusinessException invalidInput() {
        return new BusinessException(CommonErrorCode.INVALID_INPUT);
    }
}
