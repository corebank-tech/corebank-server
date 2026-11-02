package com.shinhan.corebank.transfer.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerifier;
import com.shinhan.corebank.account.domain.exception.AccountPasswordErrorCode;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
import com.shinhan.corebank.transfer.api.TransferHoldDecision;
import com.shinhan.corebank.transfer.application.port.out.TransferHoldPort;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferHold;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** MockitoBean 구성은 TransferControllerTest와 같게 맞춰 컨텍스트 캐시를 공유한다. */
@AutoConfigureMockMvc
@DisplayName("지연이체 설정 API")
class DelayedTransferControllerTest extends IntegrationTestSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    // 시드와 다른 테스트가 쓰지 않는 고객 ID 대역
    private static final long CUSTOMER_ID = 772_101L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransferHoldPort transferHoldPort;

    @Autowired
    private Clock clock;

    @MockitoBean
    private OtpAuthTokenVerifier otpAuthTokenVerifier;

    @MockitoBean
    private AccountPasswordAuthTokenVerifier accountPasswordAuthTokenVerifier;

    // 멱등 키 테이블이 고객 FK를 가져 실제 고객 행이 필요하다
    @BeforeEach
    void seedCustomer() {
        jdbcTemplate.update(
                """
                INSERT INTO customer (customer_id, user_id, password_hash, user_name, birth_date, email, phone_number,
                                      joined_at, created_at, updated_at)
                VALUES (?, 'user772101', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', '테스터772',
                        '1990-01-01', 'u772101@test.com', '01077210100', NOW(6), NOW(6), NOW(6))
                ON DUPLICATE KEY UPDATE customer_id = customer_id
                """,
                CUSTOMER_ID);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM delayed_transfer_setting WHERE customer_id = ?", CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM transfer_hold WHERE customer_id IN (?, 1)", CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM idempotency_key WHERE customer_id = ?", CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM customer WHERE customer_id = ?", CUSTOMER_ID);
    }

    @Test
    @DisplayName("저장한 적 없으면 꺼짐과 기본값(100만 원·3시간)을 돌려준다")
    void get_withoutSetting_returnsDefaults() throws Exception {
        mockMvc.perform(get("/transfers/delayed-transfer-setting").with(authentication(authenticationOf())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.thresholdAmount").value(1_000_000))
                .andExpect(jsonPath("$.data.delayHours").value(3));
    }

    @Test
    @DisplayName("켜기는 토큰 없이 저장되고 조회에 반영된다")
    void put_enable_withoutToken() throws Exception {
        putSetting(true, 500_000L, 5, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.thresholdAmount").value(500_000))
                .andExpect(jsonPath("$.data.delayHours").value(5));

        mockMvc.perform(get("/transfers/delayed-transfer-setting").with(authentication(authenticationOf())))
                .andExpect(jsonPath("$.data.enabled").value(true));
        verify(accountPasswordAuthTokenVerifier, never()).verifyAndConsume(anyString(), anyLong());
    }

    @Test
    @DisplayName("켠 상태에서 끄려면 계좌비밀번호 토큰이 필요하다. 없으면 CMN0002이고 설정은 그대로다")
    void put_disable_withoutToken_isRejected() throws Exception {
        putSetting(true, 500_000L, 5, null).andExpect(status().isOk());

        putSetting(false, 500_000L, 5, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0002"));

        assertThat(storedSetting().get("enabled")).isEqualTo(true);
    }

    @Test
    @DisplayName("켠 상태에서 끄기를 토큰과 함께 보내면 토큰을 고객 단위로 검증·소비하고 저장한다")
    void put_disable_withToken_consumesToken() throws Exception {
        putSetting(true, 500_000L, 5, null).andExpect(status().isOk());

        putSetting(false, 500_000L, 5, "password-token")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));

        verify(accountPasswordAuthTokenVerifier).verifyAndConsume("password-token", CUSTOMER_ID);
    }

    @Test
    @DisplayName("토큰이 무효면 APW0102이고 설정은 그대로다")
    void put_loosen_withInvalidToken_isRejected() throws Exception {
        putSetting(true, 500_000L, 5, null).andExpect(status().isOk());
        willThrow(new BusinessException(AccountPasswordErrorCode.INVALID_AUTH_TOKEN))
                .given(accountPasswordAuthTokenVerifier)
                .verifyAndConsume(eq("bad-token"), any(Long.class));

        putSetting(true, 900_000L, 5, "bad-token")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("APW0102"));

        assertThat(((Number) storedSetting().get("threshold_amount")).longValue())
                .isEqualTo(500_000L);
    }

    @Test
    @DisplayName("범위를 벗어나면 토큰을 쓰기 전에 TRF0009로 거부한다")
    void put_outOfRange_isRejectedBeforeToken() throws Exception {
        putSetting(true, 1_000_000L, 2, "password-token")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRF0009"));

        verify(accountPasswordAuthTokenVerifier, never()).verifyAndConsume(anyString(), anyLong());
    }

    @Test
    @DisplayName("보류를 취소하면 200과 CANCELLED이고, 다시 취소하면 TRF0311, 남의 보류는 TRF0203이다")
    void cancelHold() throws Exception {
        long mine = saveHold(CUSTOMER_ID);
        long others = saveHold(1L);

        postCancel(mine)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.transferHoldId").value(mine))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        postCancel(mine)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRF0311"));
        postCancel(others)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRF0203"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM transfer_hold WHERE transfer_hold_id = ?", String.class, others))
                .isEqualTo("HELD");
    }

    private long saveHold(long customerId) {
        // 서비스와 같은 Clock(KST)을 쓴다. JVM 기본 시간대(CI는 UTC)로 만들면 취소 시한이 이미 지난 보류가 된다
        LocalDateTime now = LocalDateTime.now(clock);
        return transferHoldPort
                .save(TransferHold.hold(
                        customerId,
                        101L,
                        "110222222222",
                        30_000L,
                        null,
                        null,
                        TransferChannel.WB,
                        null,
                        TransferHoldDecision.holdUntil("TEST", now.plusHours(3)),
                        now,
                        now))
                .getTransferHoldId();
    }

    private ResultActions postCancel(long transferHoldId) throws Exception {
        return mockMvc.perform(post("/transfers/holds/{id}/cancel", transferHoldId)
                .with(authentication(authenticationOf()))
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()));
    }

    private ResultActions putSetting(boolean enabled, long thresholdAmount, int delayHours, String token)
            throws Exception {
        var request = put("/transfers/delayed-transfer-setting")
                .with(authentication(authenticationOf()))
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(
                        Map.of("enabled", enabled, "thresholdAmount", thresholdAmount, "delayHours", delayHours)));
        if (token != null) {
            request.header("Account-Password-Auth-Token", token);
        }
        return mockMvc.perform(request);
    }

    private Map<String, Object> storedSetting() {
        return jdbcTemplate.queryForMap("SELECT * FROM delayed_transfer_setting WHERE customer_id = ?", CUSTOMER_ID);
    }

    private static UsernamePasswordAuthenticationToken authenticationOf() {
        AuthenticatedCustomer customer = new AuthenticatedCustomer(CUSTOMER_ID, "user" + CUSTOMER_ID, "테스터");
        return UsernamePasswordAuthenticationToken.authenticated(
                customer, null, AuthorityUtils.createAuthorityList("ROLE_CUSTOMER"));
    }
}
