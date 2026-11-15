package com.shinhan.corebank.subscription.api;

public interface TerminationPreviewUseCase {

    TerminationPreviewResult previewMaturity(Long customerId, Long accountId);

    TerminationPreviewResult previewEarly(Long customerId, Long accountId);
}
