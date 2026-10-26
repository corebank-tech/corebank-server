package com.shinhan.corebank.product.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.product.api.InterestCalculationMode;
import com.shinhan.corebank.product.api.ProductInterestPolicyQueryUseCase;
import com.shinhan.corebank.product.application.port.in.ProductQueryUseCase;
import com.shinhan.corebank.product.domain.InterestPayType;
import com.shinhan.corebank.product.domain.ProductDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductInterestPolicyQueryService implements ProductInterestPolicyQueryUseCase {

    private final ProductQueryUseCase productQueryUseCase;

    @Override
    public InterestCalculationMode getMode(Long productId) {
        ProductDetail detail = productQueryUseCase.getDetail(productId);

        if (detail == null || detail.getProduct() == null) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }

        InterestPayType payType = detail.getProduct().getInterestPayType();

        if (payType == null) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }

        return switch (payType) {
            case SIMPLE -> InterestCalculationMode.SIMPLE;
            case COMPOUND -> InterestCalculationMode.MONTHLY_COMPOUND;
        };
    }
}
