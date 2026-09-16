package com.shinhan.corebank.transfer.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoriteAccountJpaRepository extends JpaRepository<FavoriteAccountJpaEntity, Long> {
    long countByCustomerId(Long customerId);

    List<FavoriteAccountJpaEntity> findAllByCustomerIdOrderByRegisteredAtDesc(Long customerId);
}
