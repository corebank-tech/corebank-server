package com.shinhan.corebank.scheduledtransfer.application.port.out;

import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransfer;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface TransferTradeDatePort {

    // 예약 건이 만든 이체 행의 거래일. 이체 행이 없는 건(취소·이체 전 실패)은 키가 없다
    Map<Long, LocalDate> findTradeDates(List<ScheduledTransfer> scheduledTransfers);
}
