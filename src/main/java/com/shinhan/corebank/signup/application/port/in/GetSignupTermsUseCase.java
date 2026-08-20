package com.shinhan.corebank.signup.application.port.in;

import com.shinhan.corebank.signup.domain.model.SignupTerm;

import java.util.List;

// 회원가입에 필요한 최신 약관 목록 조회 기능을 정의한다.
public interface GetSignupTermsUseCase {

    List<SignupTerm> getSignupTerms();
}
