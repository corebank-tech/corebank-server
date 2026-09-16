package com.shinhan.corebank.product.adapter.in.web;

import com.shinhan.corebank.product.domain.ProductTermsView;
import java.time.LocalDateTime;

public record ProductTermsViewResponse(
        Long termsId,
        String termsName,
        String version,
        Boolean required,
        Boolean viewRequired,
        String content,
        LocalDateTime viewedAt,
        LocalDateTime viewExpiresAt) {
    public static ProductTermsViewResponse from(ProductTermsView view) {
        return new ProductTermsViewResponse(
                view.getTermsId(),
                view.getTermsName(),
                view.getVersion(),
                view.getRequired(),
                view.getViewRequired(),
                view.getContent(),
                view.getViewedAt(),
                view.getViewExpiresAt());
    }
}
