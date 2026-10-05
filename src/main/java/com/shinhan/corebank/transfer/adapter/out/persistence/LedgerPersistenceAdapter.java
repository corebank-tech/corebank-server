package com.shinhan.corebank.transfer.adapter.out.persistence;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.transfer.application.port.out.LedgerLookupPort;
import com.shinhan.corebank.transfer.application.port.out.LedgerSavePort;
import com.shinhan.corebank.transfer.application.port.out.PostedLedgerPair;
import com.shinhan.corebank.transfer.domain.LedgerDirection;
import com.shinhan.corebank.transfer.domain.LedgerEntry;
import com.shinhan.corebank.transfer.domain.LedgerPair;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LedgerPersistenceAdapter implements LedgerSavePort, LedgerLookupPort {

    private final LedgerEntryJpaRepository repository;
    private final LedgerEntryIdGenerator ledgerEntryIdGenerator;

    public LedgerPersistenceAdapter(
            LedgerEntryJpaRepository repository, LedgerEntryIdGenerator ledgerEntryIdGenerator) {
        this.repository = repository;
        this.ledgerEntryIdGenerator = ledgerEntryIdGenerator;
    }

    // 포트 계약(원장 2행 원자적 저장)을 호출자의 트랜잭션 유무와 무관하게 이 어댑터 스스로 보장한다.
    @Override
    @Transactional
    public void save(LedgerPair pair) {
        saveEntry(pair.getWithdrawalEntry());
        saveEntry(pair.getDepositEntry());
    }

    // 표시를 먼저 세워 이미 취소된 원장이면 반대기표를 넣기 전에 멈춘다.
    @Override
    @Transactional
    public void saveReversal(LedgerPair reversal, PostedLedgerPair original) {
        markReversed(original.withdrawal());
        markReversed(original.deposit());
        save(reversal);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PostedLedgerPair> findPostedPair(String transactionNumber) {
        List<LedgerEntry> entries = repository.findByTransactionNumber(transactionNumber).stream()
                .map(LedgerEntryMapper::toDomain)
                .toList();
        Optional<LedgerEntry> withdrawal = entries.stream()
                .filter(e -> e.getDirection() == LedgerDirection.WITHDRAWAL)
                .findFirst();
        Optional<LedgerEntry> deposit = entries.stream()
                .filter(e -> e.getDirection() == LedgerDirection.DEPOSIT)
                .findFirst();
        if (entries.size() != 2 || withdrawal.isEmpty() || deposit.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new PostedLedgerPair(withdrawal.get(), deposit.get()));
    }

    private void markReversed(LedgerEntry entry) {
        if (repository.markReversed(entry.getLedgerEntryId(), entry.getOccurredAt()) != 1) {
            throw new BusinessException(TransferErrorCode.NOT_CORRECTABLE);
        }
    }

    private void saveEntry(LedgerEntry entry) {
        LedgerEntryJpaEntity entity = LedgerEntryMapper.toEntity(entry).toBuilder()
                .ledgerEntryId(ledgerEntryIdGenerator.nextId())
                .build();
        repository.save(entity);
    }
}
