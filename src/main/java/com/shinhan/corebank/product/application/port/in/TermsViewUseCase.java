package com.shinhan.corebank.product.application.port.in;

import com.shinhan.corebank.product.domain.ProductTermsView;

public interface TermsViewUseCase {
    ProductTermsView view(Long productId, Long termsId, Long customerId);

    // 상품가입 사전검증(#68)/실행(#69)이 재검증할 조회 인터페이스 — 이번 이슈 체크리스트의 "설계" 항목.
    // 실제 소비 코드는 #68/#69 범위라 아직 없지만, 구현은 여기서 같이 채워둔다.
    boolean isViewed(Long customerId, Long termsId);
}
