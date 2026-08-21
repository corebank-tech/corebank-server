package com.shinhan.corebank.limit.adapter.in.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.ZoneId;
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
class LimitControllerTest extends IntegrationTestSupport {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final AtomicLong CUSTOMER_SEQ = new AtomicLong();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    EntityManager entityManager;

    @Test
    @DisplayName("한도와 당일 사용액이 있으면 1회·1일 한도와 사용액·잔여액을 반환한다")
    void getTransferLimit_limitAndUsageExist_returnsAllFourAmounts() throws Exception {
        Long customerId = insertCustomer();
        insertTransferLimit(customerId, 2_000_000L, 8_000_000L);
        insertDailyUsage(customerId, LocalDate.now(SEOUL), 3_000_000L);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/transfer-limits")
                        .with(authentication(authenticationOf(customerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.oneTimeLimit").value(2_000_000L))
                .andExpect(jsonPath("$.data.dailyLimit").value(8_000_000L))
                .andExpect(jsonPath("$.data.dailyUsedAmount").value(3_000_000L))
                .andExpect(jsonPath("$.data.dailyRemainingAmount").value(5_000_000L));
    }

    @Test
    @DisplayName("한도 행이 없는 고객은 정책 기본값과 사용액 0으로 응답한다")
    void getTransferLimit_noRows_returnsPolicyDefaults() throws Exception {
        Long customerId = insertCustomer();
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/transfer-limits")
                        .with(authentication(authenticationOf(customerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.oneTimeLimit").value(1_000_000L))
                .andExpect(jsonPath("$.data.dailyLimit").value(5_000_000L))
                .andExpect(jsonPath("$.data.dailyUsedAmount").value(0L))
                .andExpect(jsonPath("$.data.dailyRemainingAmount").value(5_000_000L));
    }

    @Test
    @DisplayName("인증 없이 요청하면 401을 반환한다")
    void getTransferLimit_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/transfer-limits"))
                .andExpect(status().isUnauthorized());
    }

    private UsernamePasswordAuthenticationToken authenticationOf(Long customerId) {
        AuthenticatedCustomer customer = new AuthenticatedCustomer(customerId, "user" + customerId, "테스터");
        return UsernamePasswordAuthenticationToken.authenticated(
                customer, null, AuthorityUtils.createAuthorityList("ROLE_CUSTOMER"));
    }

    private Long insertCustomer() {
        long seq = CUSTOMER_SEQ.incrementAndGet();
        entityManager.createNativeQuery(
                        "INSERT INTO customer (user_id, password_hash, user_name, birth_date, email, phone_number, "
                                + "joined_at, created_at, updated_at) "
                                + "VALUES (:userId, 'x', '홍길동', '1990-01-01', :email, '01012345678', "
                                + "NOW(), NOW(), NOW())")
                .setParameter("userId", "lmt" + seq)
                .setParameter("email", "lmt" + seq + "@test.com")
                .executeUpdate();
        return ((Number) entityManager.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
    }

    private void insertTransferLimit(Long customerId, long oneTimeLimit, long dailyLimit) {
        entityManager.createNativeQuery(
                        "INSERT INTO transfer_limit (customer_id, one_time_limit, daily_limit, "
                                + "created_at, updated_at) "
                                + "VALUES (:customerId, :oneTimeLimit, :dailyLimit, NOW(), NOW())")
                .setParameter("customerId", customerId)
                .setParameter("oneTimeLimit", oneTimeLimit)
                .setParameter("dailyLimit", dailyLimit)
                .executeUpdate();
    }

    private void insertDailyUsage(Long customerId, LocalDate usageDate, long usedAmount) {
        entityManager.createNativeQuery(
                        "INSERT INTO transfer_limit_daily_usage (customer_id, usage_date, used_amount, "
                                + "created_at, updated_at) "
                                + "VALUES (:customerId, :usageDate, :usedAmount, NOW(), NOW())")
                .setParameter("customerId", customerId)
                .setParameter("usageDate", usageDate)
                .setParameter("usedAmount", usedAmount)
                .executeUpdate();
    }
}
