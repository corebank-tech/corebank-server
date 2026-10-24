package com.shinhan.infoetl.domain;

// 레플리카에서 읽은 가입·해지 이벤트 한 건. 원천 ID를 가지고 있어서 마트로 직접 보내지 않는다.
public record SubscriptionEventRow(
        EventType eventType,
        long accountId,
        long customerId,
        long productId,
        String productCode,
        String productName,
        String productGroup) {}
