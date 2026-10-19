package com.shinhan.corebank.transfer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.transfer.domain.Transfer;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferSourceType;
import com.shinhan.corebank.transfer.domain.TransferType;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class TransferPersistenceAdapterTest extends IntegrationTestSupport {

    private static final LocalDate TRADE_DATE = LocalDate.of(2026, 8, 12);

    @Autowired
    private TransferPersistenceAdapter adapter;

    @Autowired
    private TransferJpaRepository repository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        TransferTestFixtures.seedCustomerAndAccounts(entityManager);
    }

    @Test
    @DisplayName("신규 이체를 저장하면 채번된 transferId가 채워진 도메인 객체가 반환된다")
    void save_newTransfer_assignsTransferId() {
        // given
        Transfer transfer = Transfer.create(
                "20260812WB0000000001",
                101L,
                202L,
                "110222222222",
                "성춘향",
                10000L,
                0L,
                TransferType.IMMEDIATE,
                TransferChannel.WB,
                null,
                null,
                null,
                "이체출금",
                "이체입금",
                LocalDateTime.now(),
                TRADE_DATE);

        // when
        Transfer saved = adapter.save(transfer);

        // then
        assertThat(saved.getTransferId()).isNotNull();
        assertThat(saved.getTransactionNumber()).isEqualTo("20260812WB0000000001");

        entityManager.flush();
        entityManager.clear();
        TransferJpaEntity found =
                repository.findByTransactionNumber("20260812WB0000000001").orElseThrow();
        assertThat(found.getTransferId()).isEqualTo(saved.getTransferId());
        assertThat(found.getWithdrawalAccountId()).isEqualTo(101L);
        assertThat(found.getDepositAccountId()).isEqualTo(202L);
        assertThat(found.getTradeDate()).isEqualTo(TRADE_DATE);
        assertThat(TransferMapper.toDomain(found).getTradeDate()).isEqualTo(TRADE_DATE);
    }

    @Test
    @DisplayName("executionDate가 있는 자동이체를 저장하면 execution_date 컬럼에 그대로 반영된다")
    void save_autoTransferWithExecutionDate_persistsExecutionDate() {
        // given
        LocalDate executionDate = LocalDate.of(2026, 8, 20);
        Transfer transfer = Transfer.create(
                "20260820AT0000000001",
                101L,
                202L,
                "110222222222",
                "성춘향",
                10000L,
                0L,
                TransferType.AUTO,
                TransferChannel.BT,
                TransferSourceType.AUTO,
                77L,
                executionDate,
                "이체출금",
                "이체입금",
                LocalDateTime.now(),
                TRADE_DATE);

        // when
        Transfer saved = adapter.save(transfer);
        entityManager.flush();
        entityManager.clear();

        // then
        TransferJpaEntity found =
                repository.findByTransactionNumber("20260820AT0000000001").orElseThrow();
        assertThat(found.getExecutionDate()).isEqualTo(executionDate);
        assertThat(saved.getExecutionDate()).isEqualTo(executionDate);
    }

    @Test
    @DisplayName("complete()로 상태가 바뀐 이체를 재저장하면 SUCCESS 상태와 잔액이 반영된다")
    void save_completedTransfer_persistsSuccessStatus() {
        // given
        Transfer transfer = Transfer.create(
                "20260812WB0000000002",
                101L,
                202L,
                "110222222222",
                "성춘향",
                10000L,
                0L,
                TransferType.IMMEDIATE,
                TransferChannel.WB,
                null,
                null,
                null,
                "이체출금",
                "이체입금",
                LocalDateTime.now(),
                TRADE_DATE);
        Transfer saved = adapter.save(transfer);
        saved.complete(90000L, LocalDateTime.now());

        // when
        adapter.save(saved);
        entityManager.flush();
        entityManager.clear();

        // then
        TransferJpaEntity found =
                repository.findByTransactionNumber("20260812WB0000000002").orElseThrow();
        assertThat(found.getStatus().name()).isEqualTo("SUCCESS");
        assertThat(found.getWithdrawalBalanceAfter()).isEqualTo(90000L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567890123", "1234567890123456"})
    @DisplayName("타행 이체는 입금계좌 ID·예금주 없이 13·16자리 계좌번호와 은행코드로 저장된다")
    void save_interbankTransfer_persistsWithoutDepositAccountId(String depositAccountNumber) {
        // given
        Transfer transfer = Transfer.createInterbank(
                "20261023WB0000000123",
                101L,
                "004",
                depositAccountNumber,
                50000L,
                0L,
                TransferChannel.WB,
                "생활비",
                "홍길동",
                LocalDateTime.now(),
                TRADE_DATE);

        // when
        adapter.save(transfer);
        entityManager.flush();
        entityManager.clear();

        // then
        Transfer found = TransferMapper.toDomain(
                repository.findByTransactionNumber("20261023WB0000000123").orElseThrow());
        assertThat(found.getDepositAccountNumber()).isEqualTo(depositAccountNumber);
        assertThat(found.getPayeeBankCode()).isEqualTo("004");
        assertThat(found.getDepositAccountId()).isNull();
        assertThat(found.getPayeeName()).isNull();
    }

    @Test
    @DisplayName("은행코드가 없는 당행 행은 입금계좌 ID 없이 저장할 수 없다 (ck_transfer_payee_account)")
    void save_domesticTransferWithoutDepositAccountId_rejectedByCheck() {
        Transfer transfer = Transfer.builder()
                .transactionNumber("20260812WB0000000003")
                .withdrawalAccountId(101L)
                .depositAccountNumber("110222222222")
                .payeeName("성춘향")
                .amount(10000L)
                .fee(0L)
                .transferType(TransferType.IMMEDIATE)
                .channel(TransferChannel.WB)
                .status(ProcessResultStatus.ERROR)
                .transferredAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .tradeDate(TRADE_DATE)
                .build();

        assertThatThrownBy(() -> {
                    adapter.save(transfer);
                    entityManager.flush();
                })
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("은행코드가 없는 당행 행은 예금주 없이 저장할 수 없다 (ck_transfer_payee_name)")
    void save_domesticTransferWithoutPayeeName_rejectedByCheck() {
        Transfer transfer = Transfer.builder()
                .transactionNumber("20260812WB0000000004")
                .withdrawalAccountId(101L)
                .depositAccountId(202L)
                .depositAccountNumber("110222222222")
                .amount(10000L)
                .fee(0L)
                .transferType(TransferType.IMMEDIATE)
                .channel(TransferChannel.WB)
                .status(ProcessResultStatus.ERROR)
                .transferredAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .tradeDate(TRADE_DATE)
                .build();

        assertThatThrownBy(() -> {
                    adapter.save(transfer);
                    entityManager.flush();
                })
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
