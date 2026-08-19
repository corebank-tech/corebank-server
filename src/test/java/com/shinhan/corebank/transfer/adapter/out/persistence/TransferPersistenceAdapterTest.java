package com.shinhan.corebank.transfer.adapter.out.persistence;

import java.time.LocalDateTime;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.transfer.domain.Transfer;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferType;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class TransferPersistenceAdapterTest extends IntegrationTestSupport {

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
                101L, 202L, "110222222222", "성춘향",
                10000L, 0L,
                TransferType.IMMEDIATE, TransferChannel.WB,
                null, null, null,
                "이체출금", "이체입금",
                LocalDateTime.now()
        );

        // when
        Transfer saved = adapter.save(transfer);

        // then
        assertThat(saved.getTransferId()).isNotNull();
        assertThat(saved.getTransactionNumber()).isEqualTo("20260812WB0000000001");

        entityManager.flush();
        entityManager.clear();
        TransferJpaEntity found = repository.findByTransactionNumber("20260812WB0000000001").orElseThrow();
        assertThat(found.getTransferId()).isEqualTo(saved.getTransferId());
        assertThat(found.getWithdrawalAccountId()).isEqualTo(101L);
        assertThat(found.getDepositAccountId()).isEqualTo(202L);
    }

    @Test
    @DisplayName("complete()로 상태가 바뀐 이체를 재저장하면 SUCCESS 상태와 잔액이 반영된다")
    void save_completedTransfer_persistsSuccessStatus() {
        // given
        Transfer transfer = Transfer.create(
                "20260812WB0000000002",
                101L, 202L, "110222222222", "성춘향",
                10000L, 0L,
                TransferType.IMMEDIATE, TransferChannel.WB,
                null, null, null,
                "이체출금", "이체입금",
                LocalDateTime.now()
        );
        Transfer saved = adapter.save(transfer);
        saved.complete(90000L, LocalDateTime.now());

        // when
        adapter.save(saved);
        entityManager.flush();
        entityManager.clear();

        // then
        TransferJpaEntity found = repository.findByTransactionNumber("20260812WB0000000002").orElseThrow();
        assertThat(found.getStatus().name()).isEqualTo("SUCCESS");
        assertThat(found.getWithdrawalBalanceAfter()).isEqualTo(90000L);
    }
}
