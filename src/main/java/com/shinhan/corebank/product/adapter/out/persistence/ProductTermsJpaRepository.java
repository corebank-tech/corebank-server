package com.shinhan.corebank.product.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductTermsJpaRepository extends JpaRepository<ProductTermsJpaEntity, ProductTermsJpaEntityId> {
    @Query("SELECT t FROM ProductTermsJpaEntity t WHERE t.id.productId = :productId ORDER BY t.displayOrder ASC")
    List<ProductTermsJpaEntity> findAllByProductId(@Param("productId") Long productId);
}
