package com.shinhan.corebank.customer.adapter.in.web;

import com.shinhan.corebank.customer.application.port.in.CustomerInfoResult;

import java.time.OffsetDateTime;

// 마스킹된 고객 기본정보를 HTTP 응답으로 반환한다.
public record CustomerInfoResponse(
        Long customerId,
        String userName,
        String userId,
        String birthDate,
        String phoneNumber,
        String email,
        OffsetDateTime joinedAt
) {

    // application 조회 결과를 HTTP 응답 DTO로 변환한다.
    public static CustomerInfoResponse from(CustomerInfoResult result) {
        return new CustomerInfoResponse(
                result.customerId(),
                result.userName(),
                result.userId(),
                result.birthDate(),
                result.phoneNumber(),
                result.email(),
                result.joinedAt()
        );
    }
}
