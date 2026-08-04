package com.shinhan.corebank.product.adapter.out.persistence;

import com.shinhan.corebank.product.domain.ProductPreferentialRate;
import com.shinhan.corebank.product.domain.ProductPreferentialRateId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

public interface ProductPreferentialRateJpaRepository extends JpaRepository<ProductPreferentialRate, ProductPreferentialRateId> {
}
