package com.shinhan.corebank.transfer.adapter.out.persistence;

import jakarta.persistence.EntityManager;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * transfer 도메인 통합 테스트에서 재사용하는 공용 시드 데이터(Object Mother).
 * account/customer 도메인은 아직 JPA 엔티티가 없어(raw SQL만 존재) native query로 시드한다.
 * 새 테스트가 동일한 계좌·고객 데이터가 필요하면 raw SQL을 복붙하지 말고 이 메서드를 재사용한다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TransferTestFixtures {

    /** customer_id=1, account_id=101(출금)/202(입금) 을 시드한다. */
    static void seedCustomerAndAccounts(EntityManager entityManager) {
        entityManager.createNativeQuery("""
            INSERT INTO customer (customer_id, user_id, password_hash, user_name, birth_date, email, phone_number, joined_at, created_at, updated_at)
            VALUES (1, 'user1', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', '테스터', '1990-01-01', 'test@test.com', '01012345678', NOW(6), NOW(6), NOW(6))
            ON DUPLICATE KEY UPDATE customer_id = customer_id
        """).executeUpdate();

        entityManager.createNativeQuery("""
            INSERT INTO account (account_id, account_number, customer_id, product_id, account_type, balance, status, password_hash, opened_date, created_at, updated_at)
            VALUES (101, '110111111111', 1, NULL, 'DEMAND_DEPOSIT', 100000, 'ACTIVE', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', '2026-08-01', NOW(6), NOW(6)),
                   (202, '110222222222', 1, NULL, 'DEMAND_DEPOSIT', 100000, 'ACTIVE', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', '2026-08-01', NOW(6), NOW(6))
            ON DUPLICATE KEY UPDATE account_id = account_id
        """).executeUpdate();
    }
}
