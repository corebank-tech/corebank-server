package com.shinhan.corebank.product.adapter.out.persistence;

import com.shinhan.corebank.product.domain.ProductRateTier;
import com.shinhan.corebank.product.domain.ProductRateTierId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductRateTierJpaRepository extends JpaRepository<ProductRateTier, ProductRateTierId> {
    @Query("select t from ProductRateTier t where t.id.productId = :productId")
    List<ProductRateTier> findById_ProductId(Long productId);
}
