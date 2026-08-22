package com.shinhan.corebank.terms.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TermsJpaRepository extends JpaRepository<TermsJpaEntity, Long> {
    List<TermsJpaEntity> findByTermsIdIn(List<Long> termsIds);
}
