package com.shinhan.corebank.signup.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

// customer_terms_agreement 테이블의 회원가입 약관 동의를 표현한다.
@Entity
@Table(name = "customer_terms_agreement")
public class CustomerTermsAgreementJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "agreement_id")
    private Long agreementId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "terms_id", nullable = false)
    private Long termsId;

    @Column(name = "agreed_at", nullable = false)
    private LocalDateTime agreedAt;

    protected CustomerTermsAgreementJpaEntity() {
    }

    public CustomerTermsAgreementJpaEntity(
            Long customerId,
            Long termsId,
            LocalDateTime agreedAt
    ) {
        this.customerId = customerId;
        this.termsId = termsId;
        this.agreedAt = agreedAt;
    }
}
