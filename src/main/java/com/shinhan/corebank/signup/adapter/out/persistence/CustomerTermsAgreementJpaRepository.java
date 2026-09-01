package com.shinhan.corebank.signup.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

// 고객의 회원가입 약관 동의 이력을 저장한다.
public interface CustomerTermsAgreementJpaRepository extends JpaRepository<CustomerTermsAgreementJpaEntity, Long> {}
