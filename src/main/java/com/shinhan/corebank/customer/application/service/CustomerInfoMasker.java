package com.shinhan.corebank.customer.application.service;

import com.shinhan.corebank.common.util.MaskingUtil;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.regex.Pattern;

// 고객정보 응답에 적용할 개인정보 마스킹 정책을 담당한다.
@Component
public class CustomerInfoMasker {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\d{11}$");

    // 성명은 공통 이름 마스킹 정책을 적용한다.
    public String maskUserName(String userName) {
        return MaskingUtil.maskName(userName);
    }

    // 로그인 아이디는 앞 4자리만 노출하고 나머지를 마스킹한다.
    public String maskUserId(String userId) {
        if (userId == null || userId.length() < 5) {
            throw new IllegalArgumentException(
                    "마스킹할 로그인 아이디 형식이 올바르지 않습니다."
            );
        }

        return userId.substring(0, 4)
                + "*".repeat(userId.length() - 4);
    }

    // 생년월일은 연도만 노출하고 월과 일을 마스킹한다.
    public String maskBirthDate(LocalDate birthDate) {
        if (birthDate == null) {
            throw new IllegalArgumentException(
                    "마스킹할 생년월일이 필요합니다."
            );
        }

        return "%04d-**-**".formatted(birthDate.getYear());
    }

    // 휴대폰번호는 앞 3자리와 뒤 4자리만 노출한다.
    public String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null
                || !PHONE_PATTERN.matcher(phoneNumber).matches()) {
            throw new IllegalArgumentException(
                    "마스킹할 휴대폰번호 형식이 올바르지 않습니다."
            );
        }

        return phoneNumber.substring(0, 3)
                + "****"
                + phoneNumber.substring(7);
    }

    // 이메일은 로컬파트 앞 4자리까지만 노출하고 최소 한 자를 마스킹한다.
    public String maskEmail(String email) {
        if (email == null) {
            throw new IllegalArgumentException(
                    "마스킹할 이메일이 필요합니다."
            );
        }

        int atIndex = email.indexOf('@');
        if (atIndex <= 0 || atIndex == email.length() - 1) {
            throw new IllegalArgumentException(
                    "마스킹할 이메일 형식이 올바르지 않습니다."
            );
        }

        String localPart = email.substring(0, atIndex);
        String domainPart = email.substring(atIndex);
        int visibleLength = localPart.length() <= 4
                ? Math.max(0, localPart.length() - 1)
                : 4;
        int maskedLength = localPart.length() - visibleLength;

        return localPart.substring(0, visibleLength)
                + "*".repeat(maskedLength)
                + domainPart;
    }
}
