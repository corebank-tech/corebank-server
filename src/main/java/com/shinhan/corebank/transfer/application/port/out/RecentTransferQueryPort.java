package com.shinhan.corebank.transfer.application.port.out;

import com.shinhan.corebank.transfer.api.RecentTransferAggregate;
import java.time.LocalDateTime;

/** 고객이 한 수취계좌로 보낸 성공 이체를 읽는다(#771). 셈하는 규칙은 RecentTransferQuery 계약을 따른다. */
public interface RecentTransferQueryPort {

    RecentTransferAggregate aggregateSucceeded(long customerId, String depositAccountNumber, LocalDateTime since);

    boolean existsSucceededBefore(long customerId, String depositAccountNumber, LocalDateTime before);
}
