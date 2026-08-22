package com.shinhan.corebank.customer.application.port.in;

import java.time.OffsetDateTime;

// 변경 완료된 고객의 마스킹된 연락처와 변경일시를 반환한다.
public record UpdateCustomerInfoResult(
        Long customerId,
        String phoneNumber,
        String email,
        OffsetDateTime updatedAt
) {
}
