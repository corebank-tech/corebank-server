package com.shinhan.corebank.transfer.application.port.out;

import com.shinhan.corebank.transfer.api.CounterpartyTransfer;
import java.time.LocalDateTime;
import java.util.List;

public interface CounterpartyTransferQueryPort {

    List<CounterpartyTransfer> findByAccount(long accountId, LocalDateTime fromInclusive, LocalDateTime toExclusive);
}
