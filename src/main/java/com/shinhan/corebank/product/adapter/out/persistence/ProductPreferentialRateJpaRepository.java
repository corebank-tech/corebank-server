package com.shinhan.corebank.product.adapter.out.persistence;

import com.shinhan.corebank.product.domain.ProductPreferentialRate;
import com.shinhan.corebank.product.domain.ProductPreferentialRateId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductPreferentialRateJpaRepository extends JpaRepository<ProductPreferentialRate, ProductPreferentialRateId> {
    @Query("select r from ProductPreferentialRate r where r.productPreferentialRateId.productId = :productId")
    List<ProductPreferentialRate> findById_ProductId(Long productId);
}
