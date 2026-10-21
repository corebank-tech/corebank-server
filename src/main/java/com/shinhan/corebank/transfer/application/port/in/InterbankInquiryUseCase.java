package com.shinhan.corebank.transfer.application.port.in;

/** 처리 불명(TIMEOUT) 타행 건의 결과를 조회거래로 확인해 확정한다. 자동 재송신이 아니다. */
public interface InterbankInquiryUseCase {

    /** @return 이번에 확정한 건수. 여전히 결과를 모르는 건은 다음 주기에 다시 묻는다 */
    int settleUnsettled();
}
