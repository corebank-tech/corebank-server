package com.shinhan.corebank.product.application.port.out;

import com.shinhan.corebank.product.application.ProductSortType;
import com.shinhan.corebank.product.domain.Product;
import com.shinhan.corebank.product.domain.ProductGroup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductQueryPort {
    Page<Product> search(ProductGroup productGroup, String keyword, ProductSortType sort, Pageable pageable);
}
