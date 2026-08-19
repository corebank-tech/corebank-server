package com.shinhan.corebank.transfer.application.port.in;

public interface PayeeInquiryUseCase {

    /**
     * 입금계좌번호로 예금주를 조회한다. §8-3 예외에 따라 미존재(TRF0201)와 정지·해지(TRF0301)를
     * 구분해서 던진다.
     * @param depositAccountNumber 조회할 입금계좌번호
     * @return 예금주 조회 결과
     */
    PayeeInquiryResult inquire(String depositAccountNumber);
}
