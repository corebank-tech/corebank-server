package com.shinhan.corebank.subscription.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.subscription.application.port.in.InterestCalculationCommand;
import com.shinhan.corebank.subscription.application.port.in.InterestCalculationCommand.CalculationMode;
import com.shinhan.corebank.subscription.application.port.in.InterestCalculationUseCase;
import com.shinhan.corebank.subscription.domain.InterestResult;
import com.shinhan.corebank.transfer.adapter.out.persistence.LedgerEntryIdGenerator;
import com.shinhan.corebank.transfer.adapter.out.persistence.LedgerEntryJpaEntity;
import com.shinhan.corebank.transfer.adapter.out.persistence.LedgerEntryJpaRepository;
import com.shinhan.corebank.transfer.domain.LedgerDirection;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class InterestCalculationIntegrationTest extends IntegrationTestSupport {

    private static final Long ACCOUNT_ID = 8_623_001L;

    @Autowired
    private InterestCalculationUseCase interestCalculationUseCase;

    @Autowired
    private LedgerEntryJpaRepository ledgerEntryJpaRepository;

    @Autowired
    private LedgerEntryIdGenerator ledgerEntryIdGenerator;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("단리: 실제 원장 잔액 변동과 종료일 제외 규칙을 반영한다")
    void calculateSimple_fromLedger() {
        // 9/1 계산 시작 이전 잔액: 100만원
        save(LocalDateTime.of(2026, 8, 31, 18, 0), 1_000_000L);

        // 9/3 추가 입금: 잔액 200만원
        save(LocalDateTime.of(2026, 9, 3, 10, 0), 2_000_000L);

        // 종료일 9/6: 계산에 포함되면 안 됨
        save(LocalDateTime.of(2026, 9, 6, 9, 0), 9_000_000L);

        entityManager.flush();
        entityManager.clear();

        InterestResult result = interestCalculationUseCase.calculate(new InterestCalculationCommand(
                ACCOUNT_ID,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 6),
                new BigDecimal("3.70"),
                CalculationMode.SIMPLE));

        // 9/1~2: 100만원 × 2일
        // 9/3~5: 200만원 × 3일
        // 적수 = 800만원
        // 세전이자 = floor(8,000,000 × 3.70 / 36,500) = 810원
        // 소득세 110원 + 지방소득세 10원
        assertThat(result.gross()).isEqualTo(810L);
        assertThat(result.tax()).isEqualTo(120L);
        assertThat(result.net()).isEqualTo(690L);
    }

    @Test
    @DisplayName("월복리: 실제 원장 조회와 월말 경계일 계산을 연동한다")
    void calculateMonthlyCompound_fromLedger() {
        // 1/31에 100만원 예치
        save(LocalDateTime.of(2026, 1, 31, 9, 0), 1_000_000L);

        // 만기일 거래는 이자 계산에 포함하지 않음
        save(LocalDateTime.of(2026, 4, 30, 9, 0), 9_000_000L);

        entityManager.flush();
        entityManager.clear();

        InterestResult result = interestCalculationUseCase.calculate(new InterestCalculationCommand(
                ACCOUNT_ID,
                LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 4, 30),
                new BigDecimal("3.70"),
                CalculationMode.MONTHLY_COMPOUND));

        // 1/31~2/27: 28일
        // 2/28~3/30: 31일
        // 3/31~4/29: 30일
        // 이전 월까지 누적된 이자를 매월 계산에 반영
        assertThat(result.gross()).isEqualTo(9_048L);
        assertThat(result.tax()).isEqualTo(1_380L);
        assertThat(result.net()).isEqualTo(7_668L);
    }

    private void save(LocalDateTime occurredAt, long balanceAfter) {
        Long ledgerEntryId = ledgerEntryIdGenerator.nextId();

        String date = occurredAt.toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE);

        ledgerEntryJpaRepository.save(LedgerEntryJpaEntity.builder()
                .ledgerEntryId(ledgerEntryId)
                .accountId(ACCOUNT_ID)
                .transactionNumber(String.format("%sWB%010d", date, ledgerEntryId))
                .direction(LedgerDirection.DEPOSIT)
                .amount(1_000_000L)
                .balanceAfter(balanceAfter)
                .transactionType("IMMEDIATE_TRANSFER")
                .channel(TransferChannel.WB)
                .reversed(false)
                .occurredAt(occurredAt)
                .build());
    }
}
