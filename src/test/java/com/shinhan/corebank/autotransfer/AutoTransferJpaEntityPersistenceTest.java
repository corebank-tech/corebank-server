package com.shinhan.corebank.autotransfer;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.autotransfer.adapter.out.persistence.AutoTransferJpaEntity;
import com.shinhan.corebank.autotransfer.adapter.out.persistence.AutoTransferJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

// updated_at 이 insertable=false 인데 DB 기본값도 없어 저장 자체가 실패하던 회귀를 방지한다.
// (JPA Auditing 의 @LastModifiedDate 로 전환하며 수정됨)
class AutoTransferJpaEntityPersistenceTest extends IntegrationTestSupport {

    @Autowired
    AutoTransferJpaRepository repository;

    @PersistenceContext
    EntityManager em;

    @Test
    @DisplayName("register() 로 만든 엔티티가 실제로 저장되고 updated_at 이 채워진다")
    @Transactional
    void saveAndFlush() {
        em.createNativeQuery("""
                INSERT INTO customer
                (user_id, password_hash, user_name, birth_date, email, phone_number, joined_at, created_at, updated_at)
                VALUES ('probeuser', 'x', '테스트', '1990-01-01', 'probe@test.com', '01000000000', NOW(6), NOW(6), NOW(6))
                """).executeUpdate();
        Number customerId = (Number) em.createNativeQuery("SELECT customer_id FROM customer WHERE user_id = 'probeuser'")
                .getSingleResult();

        em.createNativeQuery("""
                INSERT INTO account
                (account_number, customer_id, account_type, balance, status, password_hash, opened_date, created_at, updated_at)
                VALUES ('110123456789', :customerId, 'DEMAND_DEPOSIT', 0, 'ACTIVE', 'x', NOW(6), NOW(6), NOW(6))
                """)
                .setParameter("customerId", customerId)
                .executeUpdate();
        Number accountId = (Number) em.createNativeQuery("SELECT account_id FROM account WHERE account_number = '110123456789'")
                .getSingleResult();

        LocalDateTime now = LocalDateTime.of(2026, 8, 5, 10, 0);
        AutoTransferJpaEntity e = AutoTransferJpaEntity.register(
                customerId.longValue(), accountId.longValue(), "110987654321", "홍길동",
                10000L, 1, 15,
                LocalDate.of(2026, 8, 6), LocalDate.of(2026, 12, 6), LocalDate.of(2026, 9, 15),
                null, null, now);

        AutoTransferJpaEntity saved = repository.saveAndFlush(e);

        assertThat(saved.getAutoTransferId()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }
}
