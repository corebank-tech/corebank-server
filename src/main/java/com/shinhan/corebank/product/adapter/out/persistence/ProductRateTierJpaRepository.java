package com.shinhan.corebank.product.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRateTierJpaRepository extends JpaRepository<ProductRateTierJpaEntity, ProductRateTierJpaEntityId> {
    @Query("SELECT t FROM ProductRateTierJpaEntity t WHERE t.id.productId = :productId ORDER BY t.id.termMonths ASC")
    List<ProductRateTierJpaEntity> findAllByProductId(@Param("productId") Long productId);
}
