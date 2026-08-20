package com.shinhan.corebank.product.adapter.in.web;

import com.shinhan.corebank.product.domain.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProductDetailResponse(
        Long productId,
        String productCode,
        String productName,
        ProductGroup productGroup,
        String summary,
        String description,
        BigDecimal baseRate,
        BigDecimal maxRate,
        Long minAmount,
        Long maxAmount,
        Long amountUnit,
        int minTermMonths,
        int maxTermMonths,
        InterestPayType interestPayType,
        List<Integer> termOptions,
        List<RateTierItem> rateTiers,
        List<PreferentialRateItem> preferentialRates,
        String eligibility,
        List<String> subscriptionRestrictions,
        List<String> notices,
        SaleStatus saleStatus,
        LocalDate saleEndDate,
        List<TermsItem> terms
) {
    public static ProductDetailResponse from(ProductDetail detail) {
        Product product = detail.getProduct();
        List<RateTierItem> rateTierItems = detail.getRateTiers().stream()
                .map(RateTierItem::from)
                .toList();

        return new ProductDetailResponse(
                product.getProductId(),
                product.getProductCode(),
                product.getProductName(),
                product.getProductGroup(),
                product.getSummary(),
                product.getDescription(),
                product.getBaseRate(),
                product.getMaxRate(),
                product.getMinAmount(),
                product.getMaxAmount(),
                product.getAmountUnit(),
                product.getMinTermMonths(),
                product.getMaxTermMonths(),
                product.getInterestPayType(),
                termOptions(rateTierItems),
                rateTierItems,
                detail.getPreferentialRates().stream().map(PreferentialRateItem::from).toList(),
                product.getEligibility(),
                product.getSubscriptionRestrictions(),
                product.getNotices(),
                product.getSaleStatus(),
                product.getSaleEndDate(),
                detail.getTerms().stream().map(TermsItem::from).toList()
        );
    }

    private static List<Integer> termOptions(List<RateTierItem> rateTierItems) {
        return rateTierItems.stream()
                .map(RateTierItem::termMonths)
                .distinct()
                .sorted()
                .toList();
    }

    public record RateTierItem(Integer termMonths, BigDecimal rate) {
        static RateTierItem from(ProductRateTier tier) {
            return new RateTierItem((int) tier.getId().getTermMonths(), tier.getRate());
        }
    }

    public record PreferentialRateItem(String conditionCode, String conditionName, BigDecimal rate) {
        static PreferentialRateItem from(ProductPreferentialRate rate) {
            return new PreferentialRateItem(
                    rate.getProductPreferentialRateId().getConditionCode(),
                    rate.getConditionName(),
                    rate.getRate());
        }
    }

    public record TermsItem(
            Long termsId,
            String termsName,
            String version,
            Boolean required,
            Boolean viewRequired,
            Integer displayOrder
    ) {
        static TermsItem from(ProductTerms terms) {
            return new TermsItem(
                    terms.getId().getTermsId(),
                    terms.getTermsName(),
                    terms.getVersion(),
                    terms.getRequired(),
                    terms.getViewRequired(),
                    (int) terms.getDisplayOrder());
        }
    }
}
