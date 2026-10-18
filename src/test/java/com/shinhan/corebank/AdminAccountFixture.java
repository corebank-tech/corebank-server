package com.shinhan.corebank;

import com.shinhan.corebank.customer.api.AdminPermission;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 통합 테스트에서 관리자 계정을 만든다. S0 임시 가드(#448)를 걷어낸 뒤로는 허용 목록 대신
 * {@code customer.role} 과 {@code admin_permission} 행이 관리자를 정하므로, 관리자 API 를
 * 호출하는 테스트는 먼저 이 두 가지를 넣어야 한다.
 */
public final class AdminAccountFixture {

    /**
     * 고객 행까지 없는 테스트가 관리자 하나를 통째로 만든다. {@code user_id}·{@code email} 은
     * 유니크 제약이 있어 고객 ID 에서 파생시킨다. 비밀번호는 로그인하지 않는 테스트용 더미다.
     */
    public static void createAdmin(EntityManager entityManager, long customerId, AdminPermission... permissions) {
        entityManager
                .createNativeQuery("INSERT INTO customer (customer_id, user_id, password_hash, user_name, "
                        + "birth_date, email, phone_number, login_failure_count, account_locked, "
                        + "joined_at, created_at, updated_at) "
                        + "VALUES (:customerId, :userId, 'not-a-login-test', '관리자', '1990-01-01', "
                        + ":email, '01012345678', 0, false, NOW(6), NOW(6), NOW(6))")
                .setParameter("customerId", customerId)
                .setParameter("userId", "adm" + customerId)
                .setParameter("email", "adm" + customerId + "@example.test")
                .executeUpdate();

        grantAdmin(entityManager, customerId, permissions);
    }

    /** 이미 있는 고객 행을 관리자로 올리고 권한을 준다. 권한을 주지 않으면 role 만 ADMIN 이 된다. */
    public static void grantAdmin(EntityManager entityManager, long customerId, AdminPermission... permissions) {
        entityManager
                .createNativeQuery("UPDATE customer SET role = 'ADMIN' WHERE customer_id = :customerId")
                .setParameter("customerId", customerId)
                .executeUpdate();

        for (AdminPermission permission : permissions) {
            entityManager
                    .createNativeQuery("INSERT INTO admin_permission (customer_id, permission, granted_at) "
                            + "VALUES (:customerId, :permission, NOW(6))")
                    .setParameter("customerId", customerId)
                    .setParameter("permission", permission.name())
                    .executeUpdate();
        }
    }

    /** 테스트 트랜잭션을 쓰지 않아 EntityManager 대신 JdbcTemplate 을 쓰는 통합 테스트용이다. */
    public static void grantAdmin(JdbcTemplate jdbcTemplate, long customerId, AdminPermission... permissions) {
        jdbcTemplate.update("UPDATE customer SET role = 'ADMIN' WHERE customer_id = ?", customerId);

        for (AdminPermission permission : permissions) {
            jdbcTemplate.update(
                    "INSERT INTO admin_permission (customer_id, permission, granted_at) VALUES (?, ?, NOW(6))",
                    customerId,
                    permission.name());
        }
    }
}
