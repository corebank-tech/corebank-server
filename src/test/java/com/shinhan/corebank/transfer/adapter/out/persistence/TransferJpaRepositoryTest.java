package com.shinhan.corebank.transfer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferType;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class TransferJpaRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private TransferJpaRepository transferJpaRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        TransferTestFixtures.seedCustomerAndAccounts(entityManager);
    }

    @Test
    @DisplayName("이체 엔티티를 저장하고 거래번호(transactionNumber)로 정상 조회한다")
    void saveAndFindByTransactionNumber() {
        // given
        String txNo = "20260809WB0000000001";
        LocalDateTime now = LocalDateTime.now();

        TransferJpaEntity entity = TransferJpaEntity.builder()
                .transactionNumber(txNo)
                .withdrawalAccountId(101L)
                .depositAccountId(202L)
                .depositAccountNumber("110222222222")
                .payeeName("성춘향")
                .amount(10000L)
                .fee(0L)
                .transferType(TransferType.IMMEDIATE)
                .channel(TransferChannel.WB)
                .status(ProcessResultStatus.SUCCESS)
                .transferredAt(now)
                .build();

        // when
        TransferJpaEntity saved = transferJpaRepository.save(entity);
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(saved.getTransferId()).isNotNull();

        TransferJpaEntity found =
                transferJpaRepository.findByTransactionNumber(txNo).orElseThrow();
        assertThat(found.getWithdrawalAccountId()).isEqualTo(101L);
        assertThat(found.getDepositAccountId()).isEqualTo(202L);
        assertThat(found.getAmount()).isEqualTo(10000L);
        assertThat(found.getStatus()).isEqualTo(ProcessResultStatus.SUCCESS);
    }
}
