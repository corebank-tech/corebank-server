package com.shinhan.corebank.transfer.application.port.out;

import com.shinhan.corebank.transfer.domain.LedgerPair;

public interface LedgerSavePort {

    /**
     * 복식부기 원장 2행(출금·입금)을 원자적으로 저장한다.
     * {@link LedgerPair}는 출금/입금 행이 항상 쌍으로 생성되도록 강제하므로, 이 메서드도
     * 두 행을 한 번에 저장해야 하며 한 행만 저장되는 경로가 있어서는 안 된다.
     */
    void save(LedgerPair pair);

    /**
     * 정정 체인 반대기표 2행을 저장하고 원거래 2행에 reversed를 세운다. 원거래는 지우지 않는다.
     * 원거래 행이 이미 취소돼 있으면 TRF0305로 거부한다.
     */
    void saveReversal(LedgerPair reversal, PostedLedgerPair original);
}
