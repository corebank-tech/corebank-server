package com.shinhan.corebank.product.application.port.in;

import com.shinhan.corebank.product.application.ProductSortType;
import com.shinhan.corebank.product.domain.Product;
import com.shinhan.corebank.product.domain.ProductGroup;
import org.springframework.data.domain.Page;

public interface ProductQueryUseCase {
    Page<Product> search(ProductGroup productGroup, String keyword, ProductSortType sort, int page, int size);
}
