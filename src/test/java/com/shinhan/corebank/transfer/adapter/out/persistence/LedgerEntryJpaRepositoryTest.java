package com.shinhan.corebank.transfer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.transfer.domain.LedgerDirection;
import com.shinhan.corebank.transfer.domain.LedgerPair;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
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

    @Test
    @DisplayName("지정 기간에 원장 기표가 있었던 계좌 ID만 중복 없이 반환한다 (대사 배치 증분 대상 선별)")
    void findsDistinctAccountIdsPostedBetween() {
        // given
        saveEntry(101L, LocalDateTime.of(2026, 8, 9, 9, 0, 0), LedgerDirection.WITHDRAWAL, 1000L);
        saveEntry(202L, LocalDateTime.of(2026, 8, 9, 15, 0, 0), LedgerDirection.DEPOSIT, 1000L);
        // 같은 계좌(101)에 하루 중 두 번째 기표 - 중복 제거 확인용
        saveEntry(101L, LocalDateTime.of(2026, 8, 9, 18, 0, 0), LedgerDirection.WITHDRAWAL, 500L);
        // 대상 기간(8/9) 밖의 기표 - 결과에 섞이면 안 됨
        saveEntry(303L, LocalDateTime.of(2026, 8, 8, 23, 0, 0), LedgerDirection.WITHDRAWAL, 700L);
        entityManager.flush();
        entityManager.clear();

        // when
        List<Long> accountIds = ledgerEntryJpaRepository.findDistinctAccountIdsPostedBetween(
                LocalDateTime.of(2026, 8, 9, 0, 0, 0), LocalDateTime.of(2026, 8, 10, 0, 0, 0));

        // then
        assertThat(accountIds).containsExactlyInAnyOrder(101L, 202L);
    }

    @Test
    @DisplayName("계좌별로 입금은 더하고 출금은 뺀 원장 누적 합계를 반환한다 (대사 배치의 '원장상 잔액')")
    void sumsSignedAmountByAccountIds() {
        // given
        // 101: 입금 3000 - 출금 1000 = 2000
        saveEntry(101L, LocalDateTime.of(2026, 8, 9, 9, 0, 0), LedgerDirection.DEPOSIT, 3000L);
        saveEntry(101L, LocalDateTime.of(2026, 8, 9, 10, 0, 0), LedgerDirection.WITHDRAWAL, 1000L);
        // 202: 입금 5000
        saveEntry(202L, LocalDateTime.of(2026, 8, 9, 11, 0, 0), LedgerDirection.DEPOSIT, 5000L);
        // 조회 대상에서 뺄 계좌 - 결과에 섞이면 안 됨
        saveEntry(303L, LocalDateTime.of(2026, 8, 9, 12, 0, 0), LedgerDirection.DEPOSIT, 9000L);
        entityManager.flush();
        entityManager.clear();

        // when
        List<LedgerEntryJpaRepository.AccountLedgerSumProjection> sums =
                ledgerEntryJpaRepository.sumSignedAmountByAccountIds(List.of(101L, 202L));

        // then
        assertThat(sums)
                .extracting(
                        LedgerEntryJpaRepository.AccountLedgerSumProjection::getAccountId,
                        LedgerEntryJpaRepository.AccountLedgerSumProjection::getSignedSum)
                .containsExactlyInAnyOrder(tuple(101L, 2000L), tuple(202L, 5000L));
    }

    private void saveEntry(Long accountId, LocalDateTime occurredAt, LedgerDirection direction, long amount) {
        Long ledgerEntryId = ledgerEntryIdGenerator.nextId();
        ledgerEntryJpaRepository.save(LedgerEntryJpaEntity.builder()
                .ledgerEntryId(ledgerEntryId)
                .accountId(accountId)
                .transactionNumber(String.format("20260809WB%010d", ledgerEntryId))
                .direction(direction)
                .amount(amount)
                .balanceAfter(100000L)
                .transactionType("IMMEDIATE_TRANSFER")
                .channel(TransferChannel.WB)
                .reversed(false)
                .occurredAt(occurredAt)
                .build());
    }
}
