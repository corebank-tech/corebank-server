package com.shinhan.corebank.paymentstop.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentStopCaseJpaRepository extends JpaRepository<PaymentStopCaseJpaEntity, Long> {

    List<PaymentStopCaseJpaEntity> findByAccountIdInOrderByCaseIdDesc(Collection<Long> accountIds);

    // 본인 확인용. 사건 행의 계좌 ID만 읽고 다툼 거래는 읽지 않는다
    @Query("SELECT c.accountId FROM PaymentStopCaseJpaEntity c WHERE c.caseId = :caseId")
    Optional<Long> findAccountIdByCaseId(@Param("caseId") long caseId);

    // 본인 확인용. 이의제기와 사건을 한 번에 읽어, 없는 이의제기와 남의 이의제기가 같은 조회 한 번으로 끝난다
    @Query(
            """
        SELECT c.accountId
        FROM PaymentStopCaseJpaEntity c, ObjectionJpaEntity o
        WHERE o.objectionId = :objectionId AND c.caseId = o.caseId
        """)
    Optional<Long> findAccountIdByObjectionId(@Param("objectionId") long objectionId);
}
