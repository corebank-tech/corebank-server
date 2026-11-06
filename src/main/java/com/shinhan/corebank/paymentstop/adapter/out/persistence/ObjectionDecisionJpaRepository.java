package com.shinhan.corebank.paymentstop.adapter.out.persistence;

import com.shinhan.corebank.paymentstop.domain.DecisionStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ObjectionDecisionJpaRepository extends JpaRepository<ObjectionDecisionJpaEntity, Long> {

    Optional<ObjectionDecisionJpaEntity> findFirstByObjectionIdAndStatus(Long objectionId, DecisionStatus status);

    List<ObjectionDecisionJpaEntity> findByObjectionIdOrderByDecisionIdAsc(Long objectionId);

    List<ObjectionDecisionJpaEntity> findByObjectionIdInAndStatus(Collection<Long> objectionIds, DecisionStatus status);
}
