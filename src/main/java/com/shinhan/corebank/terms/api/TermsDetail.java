package com.shinhan.corebank.terms.api;

public record TermsDetail(
        Long termsId, String title, String version, boolean isRequired, boolean viewRequired, String content) {}
