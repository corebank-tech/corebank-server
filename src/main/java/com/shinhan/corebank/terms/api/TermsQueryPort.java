package com.shinhan.corebank.terms.api;

import java.util.List;
import java.util.Optional;

public interface TermsQueryPort {

    List<TermsSummary> findByIds(List<Long> termsIds);

    Optional<TermsDetail> findDetailById(Long termsId);
}
