package com.shinhan.corebank.transfer.adapter.out.persistence;

import com.shinhan.corebank.IntegrationTestSupport;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class AccountLockJpaRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private AccountLockJpaRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("계좌 ID로 비관적 락(findForUpdate) 조회 시 해당 계좌 레코드가 정상 반환된다")
    void findByAccountIdForUpdate_returnsAccountEntity() {
        // given
        TransferTestFixtures.seedCustomerAndAccounts(entityManager);
        entityManager.flush();
        entityManager.clear();

        // when
        AccountLockJpaEntity found = repository
                .findByAccountIdForUpdate(101L)
                .orElseThrow();

        // then
        assertThat(found.getAccountId()).isEqualTo(101L);
        assertThat(found.getBalance()).isEqualTo(100000L);
        assertThat(found.getStatus()).isEqualTo("ACTIVE");
    }
}
