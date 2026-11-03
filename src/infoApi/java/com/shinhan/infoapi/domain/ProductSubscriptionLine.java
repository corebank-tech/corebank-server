package com.shinhan.infoapi.domain;

public record ProductSubscriptionLine(
        long productId,
        String productCode,
        String productName,
        String productGroup,
        long subscribedCount,
        long closedCount) {}
