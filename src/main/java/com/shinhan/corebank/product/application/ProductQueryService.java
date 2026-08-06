package com.shinhan.corebank.product.application;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.product.application.port.in.ProductQueryUseCase;
import com.shinhan.corebank.product.application.port.out.ProductQueryPort;
import com.shinhan.corebank.product.domain.Product;
import com.shinhan.corebank.product.domain.ProductGroup;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductQueryService implements ProductQueryUseCase {
    private static final Set<Integer> ALLOWED_PAGE_SIZES = Set.of(5, 10, 20, 30, 50);

    private final ProductQueryPort productQueryPort;

    @Override
    public Page<Product> search(ProductGroup productGroup, String keyword, ProductSortType sort, int page, int size) {
        if (!ALLOWED_PAGE_SIZES.contains(size)) {
            throw new BusinessException(CommonErrorCode.INVALID_PAGE_SIZE);
        }
        // TODO: 페이지 크기 검증 추출해야함

        return productQueryPort.search(productGroup, keyword, sort, PageRequest.of(page, size));
    }
}
