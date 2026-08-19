package com.shinhan.corebank.signup.application.port.in;

import com.shinhan.corebank.signup.domain.model.SignupTerm;

import java.util.List;

public interface GetSignupTermsUseCase {

    List<SignupTerm> getSignupTerms();
}