package com.shinhan.corebank.transfer.application.port.in;

// 타행 계좌의 예금주를 이체 전에 상대 은행에 묻는다(#680)
public interface InterbankPayeeInquiryUseCase {

    InterbankPayeeInquiryResult inquire(String bankCode, String accountNumber);
}
