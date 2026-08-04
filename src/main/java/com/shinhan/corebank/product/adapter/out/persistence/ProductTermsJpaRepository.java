package com.shinhan.corebank.product.adapter.out.persistence;

import com.shinhan.corebank.product.domain.ProductTerms;
import com.shinhan.corebank.product.domain.ProductTermsId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductTermsJpaRepository extends JpaRepository<ProductTerms, ProductTermsId> {
}
