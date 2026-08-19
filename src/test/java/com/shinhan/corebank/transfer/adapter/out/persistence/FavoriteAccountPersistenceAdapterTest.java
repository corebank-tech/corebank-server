package com.shinhan.corebank.transfer.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.List;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.transfer.domain.FavoriteAccount;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class FavoriteAccountPersistenceAdapterTest extends IntegrationTestSupport {

    @Autowired
    private FavoriteAccountPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("저장하면 채번된 ID를 포함한 FavoriteAccount를 반환한다")
    void save_returnsFavoriteAccountWithGeneratedId() {
        TransferTestFixtures.seedCustomerAndAccounts(entityManager);
        entityManager.flush();
        entityManager.clear();

        FavoriteAccount favoriteAccount = FavoriteAccount.register(1L, "110222222222", "테스터", "엄마",
                LocalDateTime.of(2026, 8, 18, 10, 0, 0));

        FavoriteAccount saved = adapter.save(favoriteAccount);

        assertThat(saved.getFavoriteAccountId()).isNotNull();
        assertThat(saved.getAlias()).isEqualTo("엄마");
    }

    @Test
    @DisplayName("같은 고객이 같은 입금계좌번호로 중복 등록하면 DataIntegrityViolationException이 발생한다")
    void save_duplicateCustomerAndAccountNumber_throwsDataIntegrityViolationException() {
        TransferTestFixtures.seedCustomerAndAccounts(entityManager);
        entityManager.flush();
        entityManager.clear();

        adapter.save(FavoriteAccount.register(1L, "110222222222", "테스터", "엄마",
                LocalDateTime.of(2026, 8, 18, 10, 0, 0)));
        entityManager.flush();

        assertThatThrownBy(() -> {
            adapter.save(FavoriteAccount.register(1L, "110222222222", "테스터", "우리엄마",
                    LocalDateTime.of(2026, 8, 18, 10, 1, 0)));
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("고객 ID로 등록 건수를 센다")
    void countByCustomerId_returnsCountForCustomer() {
        TransferTestFixtures.seedCustomerAndAccounts(entityManager);
        entityManager.flush();
        entityManager.clear();

        adapter.save(FavoriteAccount.register(1L, "110222222222", "테스터", "엄마",
                LocalDateTime.of(2026, 8, 18, 10, 0, 0)));
        entityManager.flush();

        assertThat(adapter.countByCustomerId(1L)).isEqualTo(1L);
        assertThat(adapter.countByCustomerId(999L)).isEqualTo(0L);
    }

    @Test
    @DisplayName("고객 ID로 등록된 모든 계좌를 최신 등록순으로 조회한다")
    void findAllByCustomerId_returnsAllForCustomerOrderedByRegisteredAtDesc() {
        TransferTestFixtures.seedCustomerAndAccounts(entityManager);
        entityManager.flush();
        entityManager.clear();

        adapter.save(FavoriteAccount.register(1L, "110111111111", "테스터", "내계좌",
                LocalDateTime.of(2026, 8, 18, 9, 0, 0)));
        adapter.save(FavoriteAccount.register(1L, "110222222222", "테스터", "엄마",
                LocalDateTime.of(2026, 8, 18, 10, 0, 0)));
        entityManager.flush();
        entityManager.clear();

        List<FavoriteAccount> result = adapter.findAllByCustomerId(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getAlias()).isEqualTo("엄마");
        assertThat(result.get(1).getAlias()).isEqualTo("내계좌");
    }
}
