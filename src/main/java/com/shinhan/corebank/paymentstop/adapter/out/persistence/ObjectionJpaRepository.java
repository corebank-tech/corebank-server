package com.shinhan.corebank.paymentstop.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ObjectionJpaRepository extends JpaRepository<ObjectionJpaEntity, Long> {

    boolean existsByCaseId(Long caseId);

    List<ObjectionJpaEntity> findAllByOrderByObjectionIdDesc(Pageable pageable);

    List<ObjectionJpaEntity> findByCaseIdIn(Collection<Long> caseIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from ObjectionJpaEntity o where o.objectionId = :objectionId")
    Optional<ObjectionJpaEntity> findByIdForUpdate(@Param("objectionId") Long objectionId);
}
