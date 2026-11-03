package com.shinhan.corebank.transfer.application.port.in;

public interface TransferHoldReleaseBatchUseCase {

    /** 멈춘 실행을 마무리하고, 해제 시각이 지난 보류를 실행한다. */
    TransferHoldReleaseSummary releaseDue();
}
