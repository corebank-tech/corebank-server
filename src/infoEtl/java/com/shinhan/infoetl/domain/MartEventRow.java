package com.shinhan.infoetl.domain;

// 마트에 쓰는 이벤트 한 줄. 계좌·고객은 가명 키만 가진다.
public record MartEventRow(
        EventType eventType,
        String accountKey,
        String customerKey,
        long productId,
        String productCode,
        String productName,
        String productGroup) {}
