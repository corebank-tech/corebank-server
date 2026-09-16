package com.shinhan.corebank.transfer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.transfer.application.port.in.TransferResult;
import com.shinhan.corebank.transfer.domain.Transfer;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferSourceType;
import com.shinhan.corebank.transfer.domain.TransferType;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class TransferLookupPersistenceAdapterTest extends IntegrationTestSupport {

    @Autowired
    private TransferLookupPersistenceAdapter lookupAdapter;

    @Autowired
    private TransferPersistenceAdapter saveAdapter;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        TransferTestFixtures.seedCustomerAndAccounts(entityManager);
    }

    @Test
    @DisplayName("동일 sourceType+sourceId+executionDate로 저장된 이체가 있으면 그 결과를 반환한다")
    void findBySourceAndExecutionDate_existingTransfer_returnsResult() {
        // given
        LocalDate executionDate = LocalDate.of(2026, 8, 20);
        Transfer transfer = Transfer.create(
                "20260820AT0000000002",
                101L,
                202L,
                "110222222222",
                "성춘향",
                10000L,
                0L,
                TransferType.AUTO,
                TransferChannel.BT,
                TransferSourceType.AUTO,
                88L,
                executionDate,
                "이체출금",
                "이체입금",
                LocalDateTime.of(2026, 8, 20, 9, 0, 0));
        transfer.complete(90000L, LocalDateTime.of(2026, 8, 20, 9, 0, 1));
        saveAdapter.save(transfer);
        entityManager.flush();
        entityManager.clear();

        // when
        Optional<TransferResult> found =
                lookupAdapter.findBySourceAndExecutionDate(TransferSourceType.AUTO, 88L, executionDate);

        // then
        assertThat(found).isPresent();
        assertThat(found.get().status()).isEqualTo(ProcessResultStatus.SUCCESS);
        assertThat(found.get().transactionNumber()).isEqualTo("20260820AT0000000002");
    }

    @Test
    @DisplayName("해당 sourceType+sourceId+executionDate로 저장된 이체가 없으면 빈 값을 반환한다")
    void findBySourceAndExecutionDate_noTransfer_returnsEmpty() {
        // when
        Optional<TransferResult> found =
                lookupAdapter.findBySourceAndExecutionDate(TransferSourceType.AUTO, 999L, LocalDate.of(2026, 8, 20));

        // then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("거래번호로 저장된 이체가 있으면 도메인 객체를 반환한다")
    void findByTransactionNumber_existingTransfer_returnsTransfer() {
        // given
        Transfer transfer = Transfer.create(
                "20260820IT0000000099",
                101L,
                202L,
                "110222222222",
                "성춘향",
                15000L,
                0L,
                TransferType.IMMEDIATE,
                TransferChannel.BT,
                null,
                null,
                null,
                "이체출금",
                "이체입금",
                LocalDateTime.of(2026, 8, 20, 10, 0, 0));
        transfer.complete(85000L, LocalDateTime.of(2026, 8, 20, 10, 0, 1));
        saveAdapter.save(transfer);
        entityManager.flush();
        entityManager.clear();

        // when
        Optional<Transfer> found = lookupAdapter.findByTransactionNumber("20260820IT0000000099");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getWithdrawalAccountId()).isEqualTo(101L);
        assertThat(found.get().getStatus()).isEqualTo(ProcessResultStatus.SUCCESS);
        assertThat(found.get().getAmount()).isEqualTo(15000L);
    }

    @Test
    @DisplayName("해당 거래번호로 저장된 이체가 없으면 빈 값을 반환한다")
    void findByTransactionNumber_noTransfer_returnsEmpty() {
        // when
        Optional<Transfer> found = lookupAdapter.findByTransactionNumber("NOT-EXIST");

        // then
        assertThat(found).isEmpty();
    }
}
