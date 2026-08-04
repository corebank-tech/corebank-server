package com.shinhan.corebank.product.adapter.out.persistence;

import com.shinhan.corebank.product.domain.ProductRateTier;
import com.shinhan.corebank.product.domain.ProductRateTierId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRateTierJpaRepository extends JpaRepository<ProductRateTier, ProductRateTierId> {
}
