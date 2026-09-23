package com.shinhan.corebank.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * AutoTransferController·ScheduledTransferController 가 자체 withIdempotency() 대신 공용
 * IdempotentRequestExecutor 를 쓰도록 바꿔도(#408) request_hash 의 입력인 지문 JSON 이 그대로인지 고정한다.
 *
 * <p>request_hash 는 이 JSON 의 SHA-256 이라 JSON 이 같으면 해시도 같다. 기대값은 교체 전 코드에서 실제로
 * 뽑은 문자열이다. 달라지면 배포 시점에 살아 있던 멱등키(24시간)가 재생 대신 CMN0302 로 거부되고,
 * 클라이언트가 새 키로 다시 보내면 같은 이체가 두 번 등록된다.
 */
@AutoConfigureMockMvc
class IdempotencyRequestHashCompatibilityTest extends IntegrationTestSupport {

    private static final long CUSTOMER_ID = 408L;
    private static final String REPLAY_SNAPSHOT = "{\"code\":\"0000\",\"message\":\"OK\",\"data\":null}";

    @Autowired
    MockMvc mockMvc;

    // 지문 JSON 은 begin() 의 requestBody 인자로 들어온다. 여기서 잡아 비교하고, 재생 응답을 돌려줘
    // 유스케이스까지 내려가지 않게 한다 — 검증 대상은 컨트롤러가 만드는 지문뿐이다.
    @MockitoBean
    IdempotencyService idempotencyService;

    @Test
    @DisplayName("자동이체 등록 지문은 인증 토큰을 뺀 본문 필드를 선언 순서대로 담는다")
    void autoTransferRegister() throws Exception {
        String body =
                """
                {"withdrawalAccountId":1001,"depositAccountNumber":"110123456789","payeeName":"홍길동",
                 "amount":50000,"cycleMonths":1,"transferDay":25,"startDate":"2030-01-01","endDate":"2031-01-01",
                 "myPassbookMemo":"적금","recipientPassbookMemo":"홍길동",
                 "accountPasswordAuthToken":"ACC","otpAuthToken":"OTP"}
                """;

        String fingerprint = fingerprintOf(post("/auto-transfers"), "POST /auto-transfers", body);

        assertThat(fingerprint)
                .isEqualTo("{\"withdrawalAccountId\":1001,\"depositAccountNumber\":\"110123456789\","
                        + "\"payeeName\":\"홍길동\",\"amount\":50000,\"cycleMonths\":1,\"transferDay\":25,"
                        + "\"startDate\":\"2030-01-01\",\"endDate\":\"2031-01-01\","
                        + "\"myPassbookMemo\":\"적금\",\"recipientPassbookMemo\":\"홍길동\"}");
    }

    @Test
    @DisplayName("자동이체 변경 지문은 변경 불가 필드를 null 이라도 포함한다")
    void autoTransferChange() throws Exception {
        String body =
                """
                {"amount":60000,"cycleMonths":3,"endDate":"2031-01-01",
                 "myPassbookMemo":"적금","recipientPassbookMemo":"홍길동",
                 "accountPasswordAuthToken":"ACC","otpAuthToken":"OTP"}
                """;

        String fingerprint = fingerprintOf(patch("/auto-transfers/77"), "PATCH /auto-transfers/77", body);

        assertThat(fingerprint)
                .isEqualTo("{\"amount\":60000,\"cycleMonths\":3,\"endDate\":\"2031-01-01\","
                        + "\"myPassbookMemo\":\"적금\",\"recipientPassbookMemo\":\"홍길동\","
                        + "\"withdrawalAccountId\":null,\"depositAccountNumber\":null,\"transferDay\":null}");
    }

    @Test
    @DisplayName("자동이체 해지 지문은 customerId 와 정렬·중복 제거된 ID 목록이다")
    void autoTransferCancel() throws Exception {
        String fingerprint = fingerprintOf(
                post("/auto-transfers/cancel")
                        .header("Account-Password-Auth-Token", "ACC")
                        .header("Otp-Auth-Token", "OTP"),
                "POST /auto-transfers/cancel",
                "{\"autoTransferIds\":[5,4,5]}");

        assertThat(fingerprint).isEqualTo("{\"customerId\":408,\"autoTransferIds\":[4,5]}");
    }

    @Test
    @DisplayName("예약이체 등록 지문은 인증 토큰을 뺀 본문 필드를 선언 순서대로 담는다")
    void scheduledTransferRegister() throws Exception {
        String body =
                """
                {"withdrawalAccountId":1001,"depositAccountNumber":"110123456789","payeeName":"홍길동",
                 "amount":50000,"scheduledDate":"2030-01-01",
                 "myPassbookMemo":"생활비","recipientPassbookMemo":"홍길동",
                 "accountPasswordAuthToken":"ACC","otpAuthToken":"OTP"}
                """;

        String fingerprint = fingerprintOf(post("/scheduled-transfers"), "POST /scheduled-transfers", body);

        assertThat(fingerprint)
                .isEqualTo("{\"withdrawalAccountId\":1001,\"depositAccountNumber\":\"110123456789\","
                        + "\"payeeName\":\"홍길동\",\"amount\":50000,\"scheduledDate\":\"2030-01-01\","
                        + "\"myPassbookMemo\":\"생활비\",\"recipientPassbookMemo\":\"홍길동\"}");
    }

    @Test
    @DisplayName("예약이체 취소 지문은 customerId 와 정렬·중복 제거된 ID 목록이다")
    void scheduledTransferCancel() throws Exception {
        String fingerprint = fingerprintOf(
                post("/scheduled-transfers/cancel")
                        .header("Account-Password-Auth-Token", "ACC")
                        .header("Otp-Auth-Token", "OTP"),
                "POST /scheduled-transfers/cancel",
                "{\"scheduledTransferIds\":[3,1,2,1]}");

        assertThat(fingerprint).isEqualTo("{\"customerId\":408,\"scheduledTransferIds\":[1,2,3]}");
    }

    private String fingerprintOf(MockHttpServletRequestBuilder request, String endpoint, String body) throws Exception {
        when(idempotencyService.begin(any(), any(), any(), any()))
                .thenReturn(IdempotencyResult.replay((short) 200, REPLAY_SNAPSHOT));

        mockMvc.perform(request.with(authentication(authenticationOf()))
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        ArgumentCaptor<String> requestBody = ArgumentCaptor.forClass(String.class);
        verify(idempotencyService).begin(any(), eq(CUSTOMER_ID), eq(endpoint), requestBody.capture());
        return requestBody.getValue();
    }

    private UsernamePasswordAuthenticationToken authenticationOf() {
        AuthenticatedCustomer customer = new AuthenticatedCustomer(CUSTOMER_ID, "user" + CUSTOMER_ID, "테스터");
        return UsernamePasswordAuthenticationToken.authenticated(
                customer, null, AuthorityUtils.createAuthorityList("ROLE_CUSTOMER"));
    }
}
