package com.shinhan.infoetl.domain;

// 마트에 쓰는 한 줄. 계좌·고객은 가명 키만 가지고 원래 ID는 없다.
public record MartBalanceRow(
        String accountKey, String customerKey, String accountType, Long productId, String productGroup, long balance) {}
