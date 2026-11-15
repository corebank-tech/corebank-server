package com.shinhan.corebank.paymentstop.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentOcrJpaRepository extends JpaRepository<DocumentOcrJpaEntity, Long> {

    List<DocumentOcrJpaEntity> findByDocumentIdIn(Collection<Long> documentIds);
}
