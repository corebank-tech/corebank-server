package com.shinhan.corebank.transfer.adapter.out.persistence;

import com.shinhan.corebank.transfer.api.DepositType;
import com.shinhan.corebank.transfer.application.port.out.LedgerPrincipalBalancePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class LedgerPrincipalBalancePersistenceAdapter implements LedgerPrincipalBalancePort {

    private final LedgerEntryJpaRepository ledgerEntryJpaRepository;

    @Override
    public long sumNetAmountExcludingInterest(long accountId) {
        return ledgerEntryJpaRepository.sumNetAmountExcludingInterest(accountId, DepositType.INTEREST.name());
    }
}
