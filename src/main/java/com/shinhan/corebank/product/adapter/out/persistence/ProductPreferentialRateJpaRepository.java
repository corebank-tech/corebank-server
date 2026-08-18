package com.shinhan.corebank.product.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductPreferentialRateJpaRepository extends JpaRepository<ProductPreferentialRateJpaEntity, ProductPreferentialRateJpaEntityId> {
    @Query("SELECT p FROM ProductPreferentialRateJpaEntity p WHERE p.productPreferentialRateId.productId = :productId ORDER BY p.conditionName ASC")
    List<ProductPreferentialRateJpaEntity> findAllByProductId(@Param("productId") Long productId);
}
