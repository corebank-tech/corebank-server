package com.shinhan.corebank.transfer.application.port.out;

import com.shinhan.corebank.transfer.domain.Transfer;

public interface TransferSavePort {

    /**
     * 이체(Transfer) 도메인을 저장한다.
     * 신규 생성 시(transferId가 null인 상태로 전달되는 경우) DB가 채번한 transferId가
     * 채워진 도메인 객체를 반환한다. complete()/fail()로 상태가 바뀐 이체를 다시 저장할
     * 때도 동일 메서드를 사용한다.
     */
    Transfer save(Transfer transfer);
}
