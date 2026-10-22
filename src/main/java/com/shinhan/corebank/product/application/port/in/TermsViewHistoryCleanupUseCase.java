package com.shinhan.corebank.product.application.port.in;

public interface TermsViewHistoryCleanupUseCase {
    // 만료된 약관 열람 이력을 모두 지우고 지운 건수를 돌려준다.
    int deleteExpired();
}
