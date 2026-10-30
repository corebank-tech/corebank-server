package com.shinhan.corebank.product.application.port.out;

import java.util.Optional;

public interface TermsViewHistoryPort {
    TermsView record(Long customerId, Long termsId);

    Optional<TermsView> find(Long customerId, Long termsId);

    // 만료된 열람 이력을 최대 limit건 지우고 지운 건수를 돌려준다.
    int deleteExpired(int limit);
}
