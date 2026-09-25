package com.shinhan.corebank.transfer.adapter.out.persistence;

import com.shinhan.corebank.account.api.AccountBalanceQuery;
import com.shinhan.corebank.transfer.application.port.out.AccountBalanceSnapshotPort;
import java.util.Collection;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * account 도메인의 공개 계약(account.api.AccountBalanceQuery)을 통해서만 잔액을 조회한다.
 * transfer 도메인 전용 부분 매핑(AccountLockJpaEntity)은 이체 실행(락·잔액 변경)에만
 * 쓰는 좁은 목적의 승인된 예외라서, 이체 실행과 무관한 이 조회(#378 대사 배치)는
 * account 도메인 소유 계약을 거친다(PR #463 리뷰 R2, 계좌 도메인 오너 확인).
 */
@Component
public class AccountBalanceSnapshotPersistenceAdapter implements AccountBalanceSnapshotPort {

    private final AccountBalanceQuery accountBalanceQuery;

    public AccountBalanceSnapshotPersistenceAdapter(AccountBalanceQuery accountBalanceQuery) {
        this.accountBalanceQuery = accountBalanceQuery;
    }

    @Override
    public Map<Long, Long> findBalancesByAccountIds(Collection<Long> accountIds) {
        return accountBalanceQuery.findBalancesByAccountIds(accountIds);
    }
}
