package com.shinhan.corebank.account.adapter.out.persistence;

import com.shinhan.corebank.account.domain.AccountNumberSequence;
import org.springframework.stereotype.Component;

@Component
public class AccountNumberSequenceMapper {
    public AccountNumberSequence toDomain(
            AccountNumberSequenceJpaEntity entity
    ){
        return AccountNumberSequence.reconstitute(
                entity.getSequenceId(),
                entity.getBankCode(),
                entity.getAccountType(),
                entity.getProductId(),
                entity.getProductPrefix(),
                entity.getLastSequence()
        );
    }
}
