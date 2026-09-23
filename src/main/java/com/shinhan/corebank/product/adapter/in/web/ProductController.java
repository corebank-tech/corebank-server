package com.shinhan.corebank.product.adapter.in.web;

import com.shinhan.corebank.adapter.in.web.exception.ErrorResponse;
import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.common.response.PageResponse;
import com.shinhan.corebank.product.application.port.in.ProductQueryUseCase;
import com.shinhan.corebank.product.application.port.in.TermsViewUseCase;
import com.shinhan.corebank.product.domain.Product;
import com.shinhan.corebank.product.domain.ProductDetailView;
import com.shinhan.corebank.product.domain.ProductGroup;
import com.shinhan.corebank.product.domain.ProductSortType;
import com.shinhan.corebank.product.domain.ProductTermsView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@Tag(name = "상품", description = "예적금 상품 검색·상세·약관 조회 API")
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductQueryUseCase productQueryUseCase;
    private final TermsViewUseCase termsViewUseCase;
    private final CurrentCustomerProvider currentCustomerProvider;

    @GetMapping
    @Operation(
            operationId = "searchProducts",
            summary = "상품 목록 검색",
            description = "판매 상태와 무관하게 상품 그룹·키워드로 상품을 검색한다. 로그인 없이 조회할 수 있다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "상품 목록 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "`CMN0001` page가 0보다 작음 · `CMN0005` 지원하지 않는 페이지 크기(5·10·20·30·50 외)",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ApiResponse<PageResponse<ProductListItemResponse>> searchProducts(
            @Parameter(description = "상품 그룹 필터. 생략 시 전체") @RequestParam(required = false) ProductGroup productGroup,
            @Parameter(description = "상품명 검색 키워드") @RequestParam(required = false) String keyword,
            @Parameter(description = "정렬 조건. RATE(금리 높은순)·NEW(신규 상품)·NAME(상품명). 생략 시 RATE", example = "RATE")
                    @RequestParam(defaultValue = "RATE")
                    ProductSortType sort,
            @Parameter(description = "페이지 번호(0부터 시작). 0 이상", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기. 5·10·20·30·50 중 하나", example = "10") @RequestParam(defaultValue = "10")
                    int size,
            @Parameter(description = "true면 페이지 구분 없이 조건에 맞는 전체 건을 반환. page/size 값과 검증을 모두 건너뜀")
                    @RequestParam(defaultValue = "false")
                    boolean all) {
        Page<Product> result = productQueryUseCase.search(productGroup, keyword, sort, page, size, all);
        return ApiResponse.success(PageResponse.from(result, ProductListItemResponse::from));
    }

    @GetMapping("/{productId}")
    @Operation(
            operationId = "getProductDetail",
            summary = "상품 상세 조회",
            description = "상품의 금리·가입조건·약관 목록을 포함한 상세정보를 조회한다. 로그인 없이 조회할 수 있다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "상품 상세 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "`PRD0201` 상품을 찾을 수 없음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ApiResponse<ProductDetailResponse> getProductDetail(
            @Parameter(description = "조회할 상품의 내부 식별자", required = true, example = "1") @PathVariable Long productId) {
        ProductDetailView detail = productQueryUseCase.getDetailWithTerms(productId);
        return ApiResponse.success(ProductDetailResponse.from(detail));
    }

    @GetMapping("/{productId}/terms/{termsId}")
    @Operation(
            operationId = "getProductTerms",
            summary = "상품 약관 전문 조회",
            description =
                    """
                    상품에 연결된 약관의 전문을 조회한다. 로그인한 고객 단위로 열람 이력을 남기므로 인증이 필요하다 —
                    필수 약관 중 viewRequired=true인 항목은 이 조회 없이는 가입 사전검증에서 TERMS_NOT_VIEWED(PRD0005)로 거부된다.
                    """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "약관 전문 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "`CMN0101` 인증정보가 없거나 세션이 만료됨",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "`PRD0201` 상품을 찾을 수 없음 · `PRD0202` 약관을 찾을 수 없거나 이 상품에 연결되지 않음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ApiResponse<ProductTermsViewResponse> getProductTerms(
            @Parameter(description = "약관이 속한 상품의 내부 식별자", required = true, example = "1") @PathVariable Long productId,
            @Parameter(description = "조회할 약관의 내부 식별자", required = true, example = "3") @PathVariable Long termsId) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        ProductTermsView view = termsViewUseCase.view(productId, termsId, customerId);
        return ApiResponse.success(ProductTermsViewResponse.from(view));
    }
}
