package com.shinhan.corebank.transfer.application.port.out;

import java.time.LocalDateTime;
import java.util.List;

/** 즉시이체 화면의 최근 이체계좌 목록을 읽는다(#197). */
public interface RecentTransferAccountQueryPort {

    /**
     * since 이후(포함) 고객의 계좌에서 남의 당행 계좌로 보낸 성공 이체를 입금계좌별로 묶어, 마지막 이체 시각이 최신인 순으로
     * limit건 돌려준다.
     */
    List<RecentDepositAccount> findRecentDepositAccounts(long customerId, LocalDateTime since, int limit);
}
