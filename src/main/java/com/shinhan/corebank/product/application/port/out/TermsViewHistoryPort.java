package com.shinhan.corebank.product.application.port.out;

import java.util.Optional;

public interface TermsViewHistoryPort {
    TermsView record(Long customerId, Long termsId);

    Optional<TermsView> find(Long customerId, Long termsId);
}
