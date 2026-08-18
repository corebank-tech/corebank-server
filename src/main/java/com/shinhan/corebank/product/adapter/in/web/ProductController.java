package com.shinhan.corebank.product.adapter.in.web;

import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.common.response.PageResponse;
import com.shinhan.corebank.product.application.ProductSortType;
import com.shinhan.corebank.product.application.port.in.ProductQueryUseCase;
import com.shinhan.corebank.product.application.port.in.TermsViewUseCase;
import com.shinhan.corebank.product.domain.Product;
import com.shinhan.corebank.product.domain.ProductDetail;
import com.shinhan.corebank.product.domain.ProductGroup;
import com.shinhan.corebank.product.domain.ProductTermsView;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductQueryUseCase productQueryUseCase;
    private final TermsViewUseCase termsViewUseCase;
    private final CurrentCustomerProvider currentCustomerProvider;

    @GetMapping
    public ApiResponse<PageResponse<ProductListItemResponse>> searchProducts(
            @RequestParam(required = false) ProductGroup productGroup,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "RATE") ProductSortType sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<Product> result = productQueryUseCase.search(productGroup, keyword, sort, page, size);
        return ApiResponse.success(PageResponse.from(result, ProductListItemResponse::from));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductDetailResponse> getProductDetail(@PathVariable Long productId) {
        ProductDetail detail = productQueryUseCase.getDetail(productId);
        return ApiResponse.success(ProductDetailResponse.from(detail));
    }

    @GetMapping("/{productId}/terms/{termsId}")
    public ApiResponse<ProductTermsViewResponse> getProductTerms(
            @PathVariable Long productId,
            @PathVariable Long termsId) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        ProductTermsView view = termsViewUseCase.view(productId, termsId, customerId);
        return ApiResponse.success(ProductTermsViewResponse.from(view));
    }
}
