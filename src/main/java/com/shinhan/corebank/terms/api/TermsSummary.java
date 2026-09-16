package com.shinhan.corebank.terms.api;

public record TermsSummary(Long termsId, String title, String version, boolean isRequired, boolean viewRequired) {}
