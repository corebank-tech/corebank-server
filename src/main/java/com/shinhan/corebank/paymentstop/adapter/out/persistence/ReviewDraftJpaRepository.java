package com.shinhan.corebank.paymentstop.adapter.out.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewDraftJpaRepository extends JpaRepository<ReviewDraftJpaEntity, Long> {

    Optional<ReviewDraftJpaEntity> findByRunId(Long runId);

    Optional<ReviewDraftJpaEntity> findFirstByObjectionIdOrderByReviewDraftIdDesc(Long objectionId);
}
