package com.shinhan.corebank.transfer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.transfer.domain.LedgerDirection;
import com.shinhan.corebank.transfer.domain.LedgerPair;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class LedgerEntryJpaRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private LedgerEntryJpaRepository ledgerEntryJpaRepository;

    @Autowired
    private LedgerEntryIdGenerator ledgerEntryIdGenerator;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("@IdClass(LedgerEntryId.class) 적용 원장 엔티티를 영속화하고 복합키로 정상 조회한다")
    void saveAndFindByCompositeKey() {
        // given
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 9, 12, 0, 0);
        LedgerEntryJpaEntity entity = LedgerEntryJpaEntity.builder()
                .ledgerEntryId(ledgerEntryIdGenerator.nextId())
                .accountId(101L)
                .transactionNumber("20260809WB0000000001")
                .direction(LedgerDirection.WITHDRAWAL)
                .amount(10000L)
                .balanceAfter(90000L)
                .transactionType("IMMEDIATE_TRANSFER")
                .transactionContent("이체출금")
                .channel(TransferChannel.WB)
                .reversed(false)
                .occurredAt(occurredAt)
                .build();

        // when
        LedgerEntryJpaEntity saved = ledgerEntryJpaRepository.save(entity);
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(saved.getLedgerEntryId()).isNotNull();

        LedgerEntryId id = new LedgerEntryId(saved.getLedgerEntryId(), occurredAt);
        LedgerEntryJpaEntity found = ledgerEntryJpaRepository.findById(id).orElseThrow();

        assertThat(found.getAccountId()).isEqualTo(101L);
        assertThat(found.getDirection()).isEqualTo(LedgerDirection.WITHDRAWAL);
        assertThat(found.getAmount()).isEqualTo(10000L);
        assertThat(found.getTransactionNumber()).isEqualTo("20260809WB0000000001");
        assertThat(found.getOccurredAt()).isEqualTo(occurredAt);
    }

    @Test
    @DisplayName(
            "LedgerPair -> Mapper -> save 흐름에서 LedgerEntryIdGenerator로 채번한 ID를 채워 저장하면 findByLedgerEntryId로 각각 조회된다")
    void ledgerPairPersistsWithDistinctGeneratedIds() {
        // given
        LedgerPair pair = LedgerPair.forTransfer(
                500L,
                "20260809WB0000000001",
                101L,
                90000L,
                202L,
                110000L,
                10000L,
                "IMMEDIATE_TRANSFER",
                "이체출금",
                "이체입금",
                TransferChannel.WB,
                LocalDateTime.of(2026, 8, 9, 12, 0, 0));

        LedgerEntryJpaEntity withdrawalEntity = LedgerEntryMapper.toEntity(pair.getWithdrawalEntry()).toBuilder()
                .ledgerEntryId(ledgerEntryIdGenerator.nextId())
                .build();
        LedgerEntryJpaEntity depositEntity = LedgerEntryMapper.toEntity(pair.getDepositEntry()).toBuilder()
                .ledgerEntryId(ledgerEntryIdGenerator.nextId())
                .build();

        // when
        LedgerEntryJpaEntity savedWithdrawal = ledgerEntryJpaRepository.save(withdrawalEntity);
        LedgerEntryJpaEntity savedDeposit = ledgerEntryJpaRepository.save(depositEntity);
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(savedWithdrawal.getLedgerEntryId()).isNotNull();
        assertThat(savedDeposit.getLedgerEntryId()).isNotNull();
        assertThat(savedWithdrawal.getLedgerEntryId()).isNotEqualTo(savedDeposit.getLedgerEntryId());

        LedgerEntryJpaEntity foundWithdrawal = ledgerEntryJpaRepository
                .findByLedgerEntryId(savedWithdrawal.getLedgerEntryId())
                .orElseThrow();
        assertThat(foundWithdrawal.getDirection()).isEqualTo(LedgerDirection.WITHDRAWAL);
        assertThat(foundWithdrawal.getTransferId()).isEqualTo(500L);
    }

    @Test
    @DisplayName("occurredAt에 나노초가 포함되면 DB round-trip 후 마이크로초 이하 정밀도로 절삭/반올림되어 원본과 달라진다")
    void occurredAtLosesSubMicrosecondPrecisionOnDatabaseRoundTrip() {
        // given
        // NOTE: MySQL DATETIME(6)에 나노초(9자리) 정밀도 값을 저장하면 마이크로초(6자리)로 줄어드는데,
        // 이때 드라이버가 truncate 하는지 round 하는지는 이 테스트가 임의로 가정하지 않는다.
        // 실측 결과 truncate가 아니었다(직접 truncatedTo(MICROS)로 복합키를 구성해 findById 하면
        // 아예 매치되는 행이 없어 NoSuchElementException 발생). 그래서 findByLedgerEntryId로 조회한 뒤
        // "마이크로초 정밀도로 줄었다(=원본과 달라졌다)"와 "1마이크로초 이내로만 달라졌다(=심각한 손상은 아니다)"만 검증한다.
        LocalDateTime nanoTime = LocalDateTime.of(2026, 8, 9, 12, 0, 0, 123_456_789);

        LedgerEntryJpaEntity entity = LedgerEntryJpaEntity.builder()
                .ledgerEntryId(ledgerEntryIdGenerator.nextId())
                .accountId(101L)
                .transactionNumber("20260809WB0000000002")
                .direction(LedgerDirection.WITHDRAWAL)
                .amount(10000L)
                .balanceAfter(90000L)
                .transactionType("IMMEDIATE_TRANSFER")
                .channel(TransferChannel.WB)
                .reversed(false)
                .occurredAt(nanoTime)
                .build();

        // when
        LedgerEntryJpaEntity saved = ledgerEntryJpaRepository.save(entity);
        entityManager.flush();
        entityManager.clear();

        // then
        LedgerEntryJpaEntity found = ledgerEntryJpaRepository
                .findByLedgerEntryId(saved.getLedgerEntryId())
                .orElseThrow();
        assertThat(found.getOccurredAt()).isNotEqualTo(nanoTime);
        assertThat(Duration.between(nanoTime, found.getOccurredAt()).abs())
                .isLessThanOrEqualTo(Duration.of(1, ChronoUnit.MICROS));
    }
}
