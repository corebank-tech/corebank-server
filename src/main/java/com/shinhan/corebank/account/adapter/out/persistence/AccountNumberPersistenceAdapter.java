package com.shinhan.corebank.account.adapter.out.persistence;

import com.shinhan.corebank.account.application.port.out.AccountNumberSequencePort;
import com.shinhan.corebank.account.domain.AccountNumberSequence;
import com.shinhan.corebank.account.domain.AccountType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AccountNumberPersistenceAdapter implements AccountNumberSequencePort {

    private final AccountNumberSequenceJpaRepository repository;
    private final AccountNumberSequenceMapper mapper;

    @Override
    public Optional<AccountNumberSequence> findForUpdate(String bankCode, AccountType accountType, Long productId) {
        Optional<AccountNumberSequenceJpaEntity> entity;

        if(productId==null){
            entity=repository.findDemandDepositForUpdate(
                    bankCode,
                    accountType
            );
        }else{
            entity=repository.findProductAccountForUpdate(
                    bankCode,
                    accountType,
                    productId
            );
        }
        return entity.map(mapper::toDomain);
    }

    @Override
    public void update(AccountNumberSequence accountNumberSequence) {
        AccountNumberSequenceJpaEntity entity=repository.findById(accountNumberSequence.getSequenceId())
                .orElseThrow(()->
                        new IllegalStateException(
                                "잠금 조회한 채번 행이 존재하지 않습니다."
                        )
                );
        entity.updateLastSequence(
                accountNumberSequence.getLastSequence()
        );
    }
}
