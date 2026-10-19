package com.shinhan.corebank.transfer.adapter.out.persistence;

import com.shinhan.corebank.transfer.application.port.out.InterbankTransferPort;
import com.shinhan.corebank.transfer.domain.Transfer;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class InterbankTransferPersistenceAdapter implements InterbankTransferPort {

    private final TransferJpaRepository repository;

    // 락은 확정 트랜잭션이 끝날 때까지 쥐어야 의미가 있어 호출자 트랜잭션 안에서만 돈다.
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Transfer> findByTransactionNumberForUpdate(String transactionNumber) {
        return repository.findByTransactionNumberForUpdate(transactionNumber).map(TransferMapper::toDomain);
    }
}
