package com.shinhan.corebank.transfer.application.port.out;

import com.shinhan.corebank.transfer.domain.DelayedTransferSetting;
import java.time.LocalDateTime;
import java.util.Optional;

public interface DelayedTransferSettingPort {

    /** 훅 C가 읽는다. 락을 잡지 않는다. */
    Optional<DelayedTransferSetting> findByCustomerId(long customerId);

    /** 설정 변경이 읽는다. 읽고 검사하고 쓰는 사이에 다른 변경이 끼지 않게 행을 잠근다. */
    Optional<DelayedTransferSetting> findForUpdateByCustomerId(long customerId);

    /** 행이 없으면 만들고 있으면 바꾼다. */
    DelayedTransferSetting save(DelayedTransferSetting setting, LocalDateTime now);
}
