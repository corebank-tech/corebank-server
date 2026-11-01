package com.shinhan.corebank.transfer.application.port.in;

public interface DelayedTransferSettingUseCase {

    /** 저장한 적 없으면 꺼짐과 기본값을 돌려준다. */
    DelayedTransferSettingResult get(long customerId);

    DelayedTransferSettingResult change(DelayedTransferSettingCommand command);
}
