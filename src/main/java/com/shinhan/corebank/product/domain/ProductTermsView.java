package com.shinhan.corebank.product.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@Builder
public class ProductTermsView {
    private Long termsId;
    private String termsName;
    private String version;
    private Boolean required;
    private Boolean viewRequired;
    private String content;
    private LocalDateTime viewedAt;
    private LocalDateTime viewExpiresAt;
}
