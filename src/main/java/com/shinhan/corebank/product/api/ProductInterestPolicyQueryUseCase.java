package com.shinhan.corebank.product.api;

/** 예적금 이자 계산에 필요한 상품 정책 조회 계약. */
public interface ProductInterestPolicyQueryUseCase {

    InterestCalculationMode getMode(Long productId);
}
