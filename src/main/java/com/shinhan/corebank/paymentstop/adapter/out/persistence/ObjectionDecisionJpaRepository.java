package com.shinhan.corebank.paymentstop.adapter.out.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ObjectionDecisionJpaRepository extends JpaRepository<ObjectionDecisionJpaEntity, Long> {

    Optional<ObjectionDecisionJpaEntity> findByObjectionId(Long objectionId);
}
