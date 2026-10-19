package com.shinhan.infoetl;

public record BalanceRow(
        long accountId, long customerId, String accountType, Long productId, String productGroup, long balance) {}
