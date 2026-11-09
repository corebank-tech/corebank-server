package com.shinhan.corebank.paymentstop.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplementRequestJpaRepository extends JpaRepository<SupplementRequestJpaEntity, Long> {

    List<SupplementRequestJpaEntity> findByObjectionIdOrderByRoundAsc(Long objectionId);

    List<SupplementRequestJpaEntity> findByObjectionIdIn(Collection<Long> objectionIds);

    Optional<SupplementRequestJpaEntity> findByObjectionIdAndRound(Long objectionId, Byte round);
}
