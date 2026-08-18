package com.shinhan.corebank.customer.adapter.in.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.common.audit.AuditEventType;
import com.shinhan.corebank.common.audit.AuditLogJpaEntity;
import com.shinhan.corebank.common.audit.AuditLogJpaRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class LoginStatusControllerTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuditLogJpaRepository auditLogJpaRepository;

    @Autowired
    EntityManager entityManager;

    private static final AtomicLong CUSTOMER_SEQ = new AtomicLong();
    private static final AtomicLong ACCOUNT_SEQ = new AtomicLong();

    @Test
    @DisplayName("직전 로그인 일시·현재 접속 IP·보유 계좌 중 가장 최근 거래일시를 함께 반환한다")
    void getLoginStatus_returnsPreviousLoginCurrentIpAndLastTransactionAt() throws Exception {
        Long customerId = insertCustomer();
        auditLogJpaRepository.save(AuditLogJpaEntity.of(customerId, null, AuditEventType.LOGIN,
                "1.1.1.1", true, null, LocalDateTime.of(2026, 3, 5, 10, 0)));
        auditLogJpaRepository.save(AuditLogJpaEntity.of(customerId, null, AuditEventType.LOGIN,
                "2.2.2.2", true, null, LocalDateTime.of(2026, 3, 10, 9, 0))); // 이번 로그인

        insertAccount(customerId, LocalDateTime.of(2026, 3, 1, 8, 0));
        insertAccount(customerId, LocalDateTime.of(2026, 3, 9, 15, 0)); // 가장 최근 거래
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/dashboard/login-status")
                        .with(authentication(authenticationOf(customerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.previousLoginAt").value("2026-03-05T10:00:00"))
                .andExpect(jsonPath("$.data.currentIp").value("127.0.0.1"))
                .andExpect(jsonPath("$.data.lastTransactionAt").value("2026-03-09T15:00:00"));
    }

    @Test
    @DisplayName("직전 로그인 기록이 없고 거래 이력도 없으면(첫 로그인, 신규 계좌) 해당 필드가 응답에서 빠진다")
    void getLoginStatus_noPreviousLoginNoTransactions_omitsNullFields() throws Exception {
        Long customerId = insertCustomer();
        auditLogJpaRepository.save(AuditLogJpaEntity.of(customerId, null, AuditEventType.LOGIN,
                "1.1.1.1", true, null, LocalDateTime.of(2026, 3, 10, 9, 0))); // 이번 로그인뿐

        mockMvc.perform(get("/dashboard/login-status")
                        .with(authentication(authenticationOf(customerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.previousLoginAt").doesNotExist())
                .andExpect(jsonPath("$.data.currentIp").value("127.0.0.1"))
                .andExpect(jsonPath("$.data.lastTransactionAt").doesNotExist());
    }

    @Test
    @DisplayName("인증 없이 요청하면 401을 반환한다")
    void getLoginStatus_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/dashboard/login-status"))
                .andExpect(status().isUnauthorized());
    }

    private UsernamePasswordAuthenticationToken authenticationOf(Long customerId) {
        AuthenticatedCustomer customer = new AuthenticatedCustomer(customerId, "user" + customerId, "테스터");
        return UsernamePasswordAuthenticationToken.authenticated(
                customer, null, AuthorityUtils.createAuthorityList("ROLE_CUSTOMER"));
    }

    private Long insertCustomer() {
        long seq = CUSTOMER_SEQ.incrementAndGet();
        String userId = "u" + seq;
        String email = "test" + seq + "@test.com";
        entityManager.createNativeQuery(
                        "INSERT INTO customer (user_id, password_hash, user_name, birth_date, email, phone_number, joined_at, created_at, updated_at) "
                                + "VALUES (:userId, 'x', '홍길동', '1990-01-01', :email, '01012345678', NOW(), NOW(), NOW())")
                .setParameter("userId", userId)
                .setParameter("email", email)
                .executeUpdate();
        return ((Number) entityManager.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
    }

    private void insertAccount(Long customerId, LocalDateTime lastTransactionAt) {
        String accountNumber = String.format("%012d", ACCOUNT_SEQ.incrementAndGet());
        entityManager.createNativeQuery(
                        "INSERT INTO account (account_number, customer_id, account_type, status, password_hash, "
                                + "opened_date, last_transaction_at, created_at, updated_at) "
                                + "VALUES (:accountNumber, :customerId, 'DEMAND_DEPOSIT', 'ACTIVE', 'x', NOW(), "
                                + ":lastTransactionAt, NOW(), NOW())")
                .setParameter("accountNumber", accountNumber)
                .setParameter("customerId", customerId)
                .setParameter("lastTransactionAt", lastTransactionAt)
                .executeUpdate();
    }
}
