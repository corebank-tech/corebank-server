package com.shinhan.corebank.paymentstop.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentStopCaseJpaRepository extends JpaRepository<PaymentStopCaseJpaEntity, Long> {

    List<PaymentStopCaseJpaEntity> findByAccountIdInOrderByCaseIdDesc(Collection<Long> accountIds);
}
