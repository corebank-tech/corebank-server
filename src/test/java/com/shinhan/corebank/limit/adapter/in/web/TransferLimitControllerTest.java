package com.shinhan.corebank.limit.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerifier;
import com.shinhan.corebank.account.domain.exception.AccountPasswordErrorCode;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class TransferLimitControllerTest extends IntegrationTestSupport {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final AtomicLong CUSTOMER_SEQ = new AtomicLong();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    EntityManager entityManager;

    /** OTP 발급·검증 흐름은 otp 모듈이 자기 테스트로 검증한다. 여기서는 한도 API 자체가 대상이다. */
    @MockitoBean
    OtpAuthTokenVerifier otpAuthTokenVerifier;

    @MockitoBean
    AccountPasswordAuthTokenVerifier accountPasswordAuthTokenVerifier;

    @Test
    @DisplayName("한도와 당일 사용액이 있으면 1회·1일 한도와 사용액·잔여액을 반환한다")
    void getTransferLimit_limitAndUsageExist_returnsAllFourAmounts() throws Exception {
        Long customerId = insertCustomer();
        insertTransferLimit(customerId, 2_000_000L, 8_000_000L);
        insertDailyUsage(customerId, LocalDate.now(SEOUL), 3_000_000L);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/transfer-limits").with(authentication(authenticationOf(customerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.oneTimeLimit").value(2_000_000L))
                .andExpect(jsonPath("$.data.dailyLimit").value(8_000_000L))
                .andExpect(jsonPath("$.data.dailyUsedAmount").value(3_000_000L))
                .andExpect(jsonPath("$.data.dailyRemainingAmount").value(5_000_000L));
    }

    @Test
    @DisplayName("한도 행이 없는 고객은 정책 기본값 대신 500 + LMT9001 로 거부한다")
    void getTransferLimit_noRows_rejectsWithLmt9001() throws Exception {
        // 가입 연계(REQ-TRSF-029)와 백필이 보장하므로, 행이 없다면 사용자 잘못이 아니라 데이터 결함이다.
        Long customerId = insertCustomer();
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/transfer-limits").with(authentication(authenticationOf(customerId))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("LMT9001"));
    }

    @Test
    @DisplayName("인증 없이 요청하면 401을 반환한다")
    void getTransferLimit_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/transfer-limits")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("한도를 변경하면 새 값으로 응답하고 변경 전 값이 이력에 쌓인다")
    void updateTransferLimit_validRequest_updatesAndRecordsHistory() throws Exception {
        Long customerId = insertCustomer();
        insertTransferLimit(customerId, 1_000_000L, 5_000_000L);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(put("/transfer-limits")
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(3_000_000L, 10_000_000L))
                        .with(authentication(authenticationOf(customerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.oneTimeLimit").value(3_000_000L))
                .andExpect(jsonPath("$.data.dailyLimit").value(10_000_000L));

        entityManager.flush();
        entityManager.clear();
        // 응답 본문은 저장 여부의 증인이 못 된다 - save() 가 네이티브 UPDATE 를 쏘고 입력 도메인을
        // 그대로 돌려주므로, 쓰기가 통째로 빠져도 본문은 새 값으로 나온다. 행을 직접 읽는다.
        assertThat(savedLimits(customerId)).containsExactly(3_000_000L, 10_000_000L);
        assertThat(historyBeforeValues(customerId)).containsExactly(1_000_000L, 5_000_000L);
        verify(accountPasswordAuthTokenVerifier).verifyAndConsume("ACC_PWD_TEST", customerId);
    }

    @Test
    @DisplayName("계좌비밀번호 토큰 검증에 실패하면 한도와 이력을 변경하지 않는다")
    void updateTransferLimit_invalidAccountPasswordToken_doesNotChangeLimit() throws Exception {
        Long customerId = insertCustomer();
        insertTransferLimit(customerId, 1_000_000L, 5_000_000L);
        entityManager.flush();
        entityManager.clear();
        doThrow(new BusinessException(AccountPasswordErrorCode.INVALID_AUTH_TOKEN))
                .when(accountPasswordAuthTokenVerifier)
                .verifyAndConsume("ACC_PWD_TEST", customerId);

        mockMvc.perform(put("/transfer-limits")
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(3_000_000L, 10_000_000L))
                        .with(authentication(authenticationOf(customerId))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("APW0102"));

        entityManager.flush();
        entityManager.clear();
        assertThat(savedLimits(customerId)).containsExactly(1_000_000L, 5_000_000L);
        assertThat(historyCount(customerId)).isZero();
    }

    @Test
    @DisplayName("1회 한도가 1일 한도보다 크면 LMT0004로 거부한다")
    void updateTransferLimit_oneTimeOverDaily_returnsLmt0004() throws Exception {
        Long customerId = insertCustomer();
        insertTransferLimit(customerId, 1_000_000L, 5_000_000L);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(put("/transfer-limits")
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(10_000_000L, 5_000_000L))
                        .with(authentication(authenticationOf(customerId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LMT0004"));
    }

    @Test
    @DisplayName("정책 상한(1회 5,000만원)을 넘으면 CMN0001로 거부한다")
    void updateTransferLimit_overPolicyCeiling_returnsCmn0001() throws Exception {
        Long customerId = insertCustomer();
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(put("/transfer-limits")
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(60_000_000L, 100_000_000L))
                        .with(authentication(authenticationOf(customerId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"));
    }

    @Test
    @DisplayName("인증 없이 변경을 요청하면 401을 반환한다")
    void updateTransferLimit_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(put("/transfer-limits")
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(3_000_000L, 10_000_000L)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("계좌비밀번호 인증 토큰이 누락되면 400을 반환한다")
    void updateTransferLimit_withoutAccountPasswordToken_returnsBadRequest() throws Exception {
        Long customerId = insertCustomer();

        mockMvc.perform(put("/transfer-limits")
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"oneTimeLimit": 3000000, "dailyLimit": 10000000,
                                 "otpAuthToken": "OTP_AUTH_TEST"}
                                """)
                        .with(authentication(authenticationOf(customerId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"));
    }

    private String body(long oneTimeLimit, long dailyLimit) {
        return """
                {"oneTimeLimit": %d, "dailyLimit": %d,
                 "accountPasswordAuthToken": "ACC_PWD_TEST", "otpAuthToken": "OTP_AUTH_TEST"}
                """
                .formatted(oneTimeLimit, dailyLimit);
    }

    private List<Long> savedLimits(Long customerId) {
        Object[] row = (Object[]) entityManager
                .createNativeQuery(
                        "SELECT one_time_limit, daily_limit FROM transfer_limit " + "WHERE customer_id = :customerId")
                .setParameter("customerId", customerId)
                .getSingleResult();
        return List.of(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
    }

    private List<Long> historyBeforeValues(Long customerId) {
        Object[] row = (Object[]) entityManager
                .createNativeQuery("SELECT before_one_time_limit, before_daily_limit FROM transfer_limit_history "
                        + "WHERE customer_id = :customerId")
                .setParameter("customerId", customerId)
                .getSingleResult();
        return List.of(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
    }

    private long historyCount(Long customerId) {
        return ((Number) entityManager
                        .createNativeQuery(
                                "SELECT COUNT(*) FROM transfer_limit_history WHERE customer_id = :customerId")
                        .setParameter("customerId", customerId)
                        .getSingleResult())
                .longValue();
    }

    private UsernamePasswordAuthenticationToken authenticationOf(Long customerId) {
        AuthenticatedCustomer customer = new AuthenticatedCustomer(customerId, "user" + customerId, "테스터");
        return UsernamePasswordAuthenticationToken.authenticated(
                customer, null, AuthorityUtils.createAuthorityList("ROLE_CUSTOMER"));
    }

    private Long insertCustomer() {
        long seq = CUSTOMER_SEQ.incrementAndGet();
        entityManager
                .createNativeQuery(
                        "INSERT INTO customer (user_id, password_hash, user_name, birth_date, email, phone_number, "
                                + "joined_at, created_at, updated_at) "
                                + "VALUES (:userId, 'x', '홍길동', '1990-01-01', :email, '01012345678', "
                                + "NOW(), NOW(), NOW())")
                .setParameter("userId", "lmt" + seq)
                .setParameter("email", "lmt" + seq + "@test.com")
                .executeUpdate();
        return ((Number) entityManager
                        .createNativeQuery("SELECT LAST_INSERT_ID()")
                        .getSingleResult())
                .longValue();
    }

    private void insertTransferLimit(Long customerId, long oneTimeLimit, long dailyLimit) {
        entityManager
                .createNativeQuery("INSERT INTO transfer_limit (customer_id, one_time_limit, daily_limit, "
                        + "created_at, updated_at) "
                        + "VALUES (:customerId, :oneTimeLimit, :dailyLimit, NOW(), NOW())")
                .setParameter("customerId", customerId)
                .setParameter("oneTimeLimit", oneTimeLimit)
                .setParameter("dailyLimit", dailyLimit)
                .executeUpdate();
    }

    private void insertDailyUsage(Long customerId, LocalDate usageDate, long usedAmount) {
        entityManager
                .createNativeQuery("INSERT INTO transfer_limit_daily_usage (customer_id, usage_date, used_amount, "
                        + "created_at, updated_at) "
                        + "VALUES (:customerId, :usageDate, :usedAmount, NOW(), NOW())")
                .setParameter("customerId", customerId)
                .setParameter("usageDate", usageDate)
                .setParameter("usedAmount", usedAmount)
                .executeUpdate();
    }
}
