package com.shinhan.corebank.transfer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.shinhan.corebank.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

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
        AccountLockJpaEntity found = repository.findByAccountIdForUpdate(101L).orElseThrow();

        // then
        assertThat(found.getAccountId()).isEqualTo(101L);
        assertThat(found.getBalance()).isEqualTo(100000L);
        assertThat(found.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("계좌 ID 목록으로 잔액을 락 없이 일괄 조회한다 (대사 배치의 '화면상 잔액' 비교 대상)")
    void findsBalancesByAccountIds() {
        // given
        TransferTestFixtures.seedCustomerAndAccounts(entityManager);
        entityManager.flush();
        entityManager.clear();

        // when
        List<AccountLockJpaRepository.AccountBalanceProjection> balances =
                repository.findBalancesByAccountIds(List.of(101L, 202L));

        // then
        assertThat(balances)
                .extracting(
                        AccountLockJpaRepository.AccountBalanceProjection::getAccountId,
                        AccountLockJpaRepository.AccountBalanceProjection::getBalance)
                .containsExactlyInAnyOrder(tuple(101L, 100000L), tuple(202L, 100000L));
    }
}
