package com.shinhan.corebank.signup.domain.model;

import java.time.LocalDate;

// 기존 은행 고객의 회원가입 확인 화면용 기본정보를 표현한다.
public record ExistingBankCustomerProfile(String existingBankCustomerId, String userName, LocalDate birthDate) {}
