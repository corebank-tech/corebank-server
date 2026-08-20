package com.shinhan.corebank.product.application.port.out;

import com.shinhan.corebank.product.application.ProductSortType;
import com.shinhan.corebank.product.domain.Product;
import com.shinhan.corebank.product.domain.ProductDetail;
import com.shinhan.corebank.product.domain.ProductGroup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface ProductQueryPort {
    Page<Product> search(ProductGroup productGroup, String keyword, ProductSortType sort, Pageable pageable);
    // ProductDetail.terms는 product_terms.display_order 오름차순으로 채워진다.
    Optional<ProductDetail> findDetailByProductId(Long productId);
    boolean existsProduct(Long productId);
    boolean existsProductTerms(Long productId, Long termsId);
}
