package com.shinhan.corebank.paymentstop.adapter.out.persistence;

import com.shinhan.corebank.paymentstop.domain.external.ExternalAgency;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalCheckJpaRepository extends JpaRepository<ExternalCheckJpaEntity, Long> {

    List<ExternalCheckJpaEntity> findByObjectionIdOrderByExternalCheckIdAsc(Long objectionId);

    Optional<ExternalCheckJpaEntity> findByObjectionIdAndAgency(Long objectionId, ExternalAgency agency);
}
