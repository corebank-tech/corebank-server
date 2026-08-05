package com.shinhan.corebank.product.adapter.out.persistence;

import com.shinhan.corebank.product.domain.ProductTerms;
import com.shinhan.corebank.product.domain.ProductTermsId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductTermsJpaRepository extends JpaRepository<ProductTerms, ProductTermsId> {
    @Query("select p from ProductTerms p where p.id.productId = :productId")
    List<ProductTerms> findById_ProductId(Long productId);
}
