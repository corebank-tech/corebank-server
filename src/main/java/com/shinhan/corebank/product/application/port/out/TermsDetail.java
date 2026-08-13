package com.shinhan.corebank.product.application.port.out;

public record TermsDetail(
        Long termsId,
        String title,
        String version,
        boolean isRequired,
        boolean viewRequired,
        String content
) {
}
