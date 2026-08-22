package com.shinhan.corebank.customer.adapter.in.web;

import com.shinhan.corebank.customer.application.port.in.UpdateCustomerInfoResult;

import java.time.OffsetDateTime;

// 고객정보 변경 후 마스킹된 연락처와 변경일시를 HTTP 응답으로 반환한다.
public record CustomerInfoUpdateResponse(
        Long customerId,
        String phoneNumber,
        String email,
        OffsetDateTime updatedAt
) {

    // application 변경 결과를 HTTP 응답 DTO로 변환한다.
    public static CustomerInfoUpdateResponse from(
            UpdateCustomerInfoResult result
    ) {
        return new CustomerInfoUpdateResponse(
                result.customerId(),
                result.phoneNumber(),
                result.email(),
                result.updatedAt()
        );
    }
}
