package com.shinhan.corebank.customer.application.service;

import com.shinhan.corebank.common.util.MaskingUtil;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

// 고객정보 응답에 적용할 개인정보 마스킹 정책을 담당한다.
@Component
public class CustomerInfoMasker {

    // 성명은 공통 이름 마스킹 정책을 적용한다.
    public String maskUserName(String userName) {
        return MaskingUtil.maskName(userName);
    }

    // 로그인 아이디는 앞 4자리만 노출하고 나머지를 마스킹한다.
    public String maskUserId(String userId) {
        if (userId == null || userId.length() < 5) {
            throw new IllegalArgumentException("마스킹할 로그인 아이디 형식이 올바르지 않습니다.");
        }

        return userId.substring(0, 4) + "*".repeat(userId.length() - 4);
    }

    // 생년월일은 연도만 노출하고 월과 일을 마스킹한다.
    public String maskBirthDate(LocalDate birthDate) {
        if (birthDate == null) {
            throw new IllegalArgumentException("마스킹할 생년월일이 필요합니다.");
        }

        return "%04d-**-**".formatted(birthDate.getYear());
    }

    // 휴대폰번호는 공통 휴대폰 마스킹 정책을 적용한다.
    public String maskPhoneNumber(String phoneNumber) {
        return MaskingUtil.maskPhoneNumber(phoneNumber);
    }

    // 이메일은 공통 이메일 마스킹 정책을 적용한다.
    public String maskEmail(String email) {
        return MaskingUtil.maskEmail(email);
    }
}
