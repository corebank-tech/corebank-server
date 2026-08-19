package com.shinhan.corebank.signup.application.port.out;

import com.shinhan.corebank.signup.domain.model.SignupTerm;

import java.util.List;

// 회원가입에 적용할 최신 약관 조회 기능을 추상화한다.
public interface SignupTermsQueryPort {

    List<SignupTerm> findLatestSignupTerms();
}
