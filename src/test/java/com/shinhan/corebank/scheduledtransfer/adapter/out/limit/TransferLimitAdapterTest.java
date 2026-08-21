package com.shinhan.corebank.scheduledtransfer.adapter.out.limit;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

// TransferLimitAdapter는 @Profile("prod")라 test 프로필에선 스프링 빈으로 뜨지 않는다.
// 실제 DB 조회 로직 자체를 검증하기 위해 리포지토리만 주입받아 어댑터를 직접 생성해서 호출한다.
@Transactional
class TransferLimitAdapterTest extends IntegrationTestSupport {

    @Autowired
    TransferLimitJpaRepository transferLimitJpaRepository;

    @Autowired
    EntityManager entityManager;

    private static final AtomicLong CUSTOMER_SEQ = new AtomicLong();

    private TransferLimitAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TransferLimitAdapter(transferLimitJpaRepository);
    }

    @Test
    @DisplayName("transfer_limit 행이 있으면 저장된 1회 한도를 반환한다")
    void findOneTimeLimit_rowExists_returnsStoredLimit() {
        Long customerId = insertCustomer();
        insertTransferLimit(customerId, 3_000_000L);

        assertThat(adapter.findOneTimeLimit(customerId)).isEqualTo(3_000_000L);
    }

    @Test
    @DisplayName("transfer_limit 행이 없으면 정책 기본값(100만원)을 반환한다")
    void findOneTimeLimit_noRow_returnsDefaultLimit() {
        Long customerId = insertCustomer();

        assertThat(adapter.findOneTimeLimit(customerId)).isEqualTo(1_000_000L);
    }

    @Test
    @DisplayName("존재하지 않는 고객ID도 정책 기본값을 반환한다")
    void findOneTimeLimit_unknownCustomerId_returnsDefaultLimit() {
        assertThat(adapter.findOneTimeLimit(999_999_999L)).isEqualTo(1_000_000L);
    }

    private Long insertCustomer() {
        long seq = CUSTOMER_SEQ.incrementAndGet();
        entityManager.createNativeQuery(
                        "INSERT INTO customer (user_id, password_hash, user_name, birth_date, email, phone_number, joined_at, created_at, updated_at) "
                                + "VALUES (:userId, 'x', '홍길동', '1990-01-01', :email, '01012345678', NOW(), NOW(), NOW())")
                .setParameter("userId", "u" + seq)
                .setParameter("email", "test" + seq + "@test.com")
                .executeUpdate();
        return ((Number) entityManager.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
    }

    private void insertTransferLimit(Long customerId, long oneTimeLimit) {
        entityManager.createNativeQuery(
                        "INSERT INTO transfer_limit (customer_id, one_time_limit, daily_limit, created_at, updated_at) "
                                + "VALUES (:customerId, :oneTimeLimit, :dailyLimit, NOW(), NOW())")
                .setParameter("customerId", customerId)
                .setParameter("oneTimeLimit", oneTimeLimit)
                .setParameter("dailyLimit", oneTimeLimit * 2)
                .executeUpdate();
    }
}
