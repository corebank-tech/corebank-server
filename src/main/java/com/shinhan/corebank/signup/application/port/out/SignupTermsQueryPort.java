package com.shinhan.corebank.signup.application.port.out;

import com.shinhan.corebank.signup.domain.model.SignupTerm;

import java.util.List;

public interface SignupTermsQueryPort {

    List<SignupTerm> findLatestSignupTerms();
}
