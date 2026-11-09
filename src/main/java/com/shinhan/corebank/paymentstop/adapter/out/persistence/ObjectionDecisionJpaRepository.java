package com.shinhan.corebank.paymentstop.adapter.out.persistence;

import com.shinhan.corebank.paymentstop.domain.DecisionStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ObjectionDecisionJpaRepository extends JpaRepository<ObjectionDecisionJpaEntity, Long> {

    Optional<ObjectionDecisionJpaEntity> findFirstByObjectionIdAndStatus(Long objectionId, DecisionStatus status);

    List<ObjectionDecisionJpaEntity> findByObjectionIdOrderByDecisionIdAsc(Long objectionId);

    List<ObjectionDecisionJpaEntity> findByObjectionIdInAndStatus(Collection<Long> objectionIds, DecisionStatus status);

    // 목록용. 반려되지 않은(승인 대기, 확정) 재량 판정의 이의제기 ID
    @Query(
            """
            SELECT d.objectionId FROM ObjectionDecisionJpaEntity d
            WHERE d.objectionId IN :objectionIds AND d.discretionary = true AND d.status <> :returned
            """)
    List<Long> findDiscretionaryObjectionIds(
            @Param("objectionIds") Collection<Long> objectionIds, @Param("returned") DecisionStatus returned);
}
