package com.shinhan.corebank.paymentstop.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseDisputedTransferJpaRepository extends JpaRepository<CaseDisputedTransferJpaEntity, Long> {

    List<CaseDisputedTransferJpaEntity> findByCaseIdOrderByOccurredAtAscDisputedTransferIdAsc(Long caseId);

    List<CaseDisputedTransferJpaEntity> findByCaseIdInOrderByOccurredAtAscDisputedTransferIdAsc(
            Collection<Long> caseIds);
}
