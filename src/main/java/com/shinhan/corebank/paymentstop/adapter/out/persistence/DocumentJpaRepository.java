package com.shinhan.corebank.paymentstop.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentJpaRepository extends JpaRepository<DocumentJpaEntity, Long> {

    List<DocumentJpaEntity> findByObjectionIdOrderByDocumentIdAsc(Long objectionId);

    Optional<DocumentJpaEntity> findByDocumentIdAndObjectionId(Long documentId, Long objectionId);

    long countByObjectionId(Long objectionId);
}
