package com.shinhan.corebank.transfer.application.port.in;

/** @param payeeName 상대 은행이 확인한 예금주. 마스킹하지 않은 원래 값이다 */
public record InterbankPayeeInquiryResult(String payeeName) {}
