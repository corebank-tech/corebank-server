package com.shinhan.corebank.product.adapter.out.persistence;

import com.shinhan.corebank.product.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {
}
