package com.shinhan.corebank.product.application.port.in;

import com.shinhan.corebank.product.application.ProductSortType;
import com.shinhan.corebank.product.domain.Product;
import com.shinhan.corebank.product.domain.ProductDetail;
import com.shinhan.corebank.product.domain.ProductDetailView;
import com.shinhan.corebank.product.domain.ProductGroup;
import org.springframework.data.domain.Page;

public interface ProductQueryUseCase {
    Page<Product> search(ProductGroup productGroup, String keyword, ProductSortType sort, int page, int size);

    // 상품 자체 정보만 필요한 호출자용. 약관 연결(product_terms)만 담고 terms 모듈은 조회하지 않는다.
    ProductDetail getDetail(Long productId);

    // 상품 상세조회 응답용. 연결된 약관 정보까지 채운다.
    ProductDetailView getDetailWithTerms(Long productId);
}
