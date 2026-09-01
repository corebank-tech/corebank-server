package com.shinhan.corebank.terms.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TermsJpaRepository extends JpaRepository<TermsJpaEntity, Long> {
    List<TermsJpaEntity> findByTermsIdIn(List<Long> termsIds);
}
