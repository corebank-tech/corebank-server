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
import com.shinhan.corebank.autotransfer.adapter.in.web.AutoTransferCancelRequest;
import com.shinhan.corebank.autotransfer.adapter.in.web.AutoTransferChangeRequest;
import com.shinhan.corebank.autotransfer.adapter.in.web.AutoTransferRegisterRequest;
import com.shinhan.corebank.scheduledtransfer.adapter.in.web.ScheduledTransferCancelRequest;
import com.shinhan.corebank.scheduledtransfer.adapter.in.web.ScheduledTransferRegisterRequest;
import java.time.LocalDate;
import java.util.List;
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
import tools.jackson.databind.ObjectMapper;

/**
 * AutoTransferController·ScheduledTransferController 가 자체 withIdempotency() 대신 공용
 * IdempotentRequestExecutor 를 쓰도록 바꿔도(#408) request_hash 의 입력인 지문 JSON 이 그대로인지 고정한다.
 *
 * <p>기대값은 교체 전 코드에서 뽑은 문자열이다. 달라지면 배포 시점에 살아 있던 멱등키(24시간)가
 * 재생 대신 CMN0302 로 거부되고, 클라이언트가 새 키로 다시 보내면 같은 이체가 두 번 등록된다.
 *
 * <p>단정이 객체 비교가 아니라 문자열 동일성인 이유는 키 순서다 — 순서가 뒤집혀도 equals 는 통과하지만
 * SHA-256 은 달라진다. 본문을 요청 DTO 로 만드는 것은 DTO 에 필드가 늘면 컴파일이 깨지게 해서,
 * 컨트롤러 fingerprint() 가 그 필드를 빠뜨린 것을 잡기 위해서다.
 */
@AutoConfigureMockMvc
class IdempotencyRequestHashCompatibilityTest extends IntegrationTestSupport {

    private static final long CUSTOMER_ID = 408L;
    private static final String REPLAY_SNAPSHOT = "{\"code\":\"0000\",\"message\":\"OK\",\"data\":null}";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    // 지문 JSON 은 begin() 의 requestBody 인자로 들어온다. 여기서 잡아 비교하고, 재생 응답을 돌려줘
    // 유스케이스까지 내려가지 않게 한다 — 검증 대상은 컨트롤러가 만드는 지문뿐이다.
    @MockitoBean
    IdempotencyService idempotencyService;

    @Test
    @DisplayName("자동이체 등록 지문은 인증 토큰을 뺀 본문 필드를 선언 순서대로 담는다")
    void autoTransferRegister() throws Exception {
        AutoTransferRegisterRequest request = new AutoTransferRegisterRequest(
                1001L,
                "110123456789",
                "홍길동",
                50000L,
                1,
                25,
                LocalDate.of(2030, 1, 1),
                LocalDate.of(2031, 1, 1),
                "적금",
                "홍길동",
                "ACC",
                "OTP");

        String fingerprint = fingerprintOf(post("/auto-transfers"), "POST /auto-transfers", request);

        assertThat(fingerprint)
                .isEqualTo("{\"withdrawalAccountId\":1001,\"depositAccountNumber\":\"110123456789\","
                        + "\"payeeName\":\"홍길동\",\"amount\":50000,\"cycleMonths\":1,\"transferDay\":25,"
                        + "\"startDate\":\"2030-01-01\",\"endDate\":\"2031-01-01\","
                        + "\"myPassbookMemo\":\"적금\",\"recipientPassbookMemo\":\"홍길동\"}");
    }

    @Test
    @DisplayName("자동이체 변경 지문은 변경 불가 필드를 null 이라도 포함한다")
    void autoTransferChange() throws Exception {
        AutoTransferChangeRequest request = new AutoTransferChangeRequest(
                60000L, 3, LocalDate.of(2031, 1, 1), "적금", "홍길동", null, null, null, "ACC", "OTP");

        String fingerprint = fingerprintOf(patch("/auto-transfers/77"), "PATCH /auto-transfers/77", request);

        assertThat(fingerprint)
                .isEqualTo("{\"amount\":60000,\"cycleMonths\":3,\"endDate\":\"2031-01-01\","
                        + "\"myPassbookMemo\":\"적금\",\"recipientPassbookMemo\":\"홍길동\","
                        + "\"withdrawalAccountId\":null,\"depositAccountNumber\":null,\"transferDay\":null}");
    }

    @Test
    @DisplayName("자동이체 해지 지문은 customerId 와 정렬·중복 제거된 ID 목록이다")
    void autoTransferCancel() throws Exception {
        AutoTransferCancelRequest request = new AutoTransferCancelRequest(List.of(5L, 4L, 5L));

        String fingerprint = fingerprintOf(
                post("/auto-transfers/cancel")
                        .header("Account-Password-Auth-Token", "ACC")
                        .header("Otp-Auth-Token", "OTP"),
                "POST /auto-transfers/cancel",
                request);

        assertThat(fingerprint).isEqualTo("{\"customerId\":408,\"autoTransferIds\":[4,5]}");
    }

    @Test
    @DisplayName("예약이체 등록 지문은 인증 토큰을 뺀 본문 필드를 선언 순서대로 담는다")
    void scheduledTransferRegister() throws Exception {
        ScheduledTransferRegisterRequest request = new ScheduledTransferRegisterRequest(
                1001L, "110123456789", "홍길동", 50000L, LocalDate.of(2030, 1, 1), "생활비", "홍길동", "ACC", "OTP");

        String fingerprint = fingerprintOf(post("/scheduled-transfers"), "POST /scheduled-transfers", request);

        assertThat(fingerprint)
                .isEqualTo("{\"withdrawalAccountId\":1001,\"depositAccountNumber\":\"110123456789\","
                        + "\"payeeName\":\"홍길동\",\"amount\":50000,\"scheduledDate\":\"2030-01-01\","
                        + "\"myPassbookMemo\":\"생활비\",\"recipientPassbookMemo\":\"홍길동\"}");
    }

    @Test
    @DisplayName("예약이체 취소 지문은 customerId 와 정렬·중복 제거된 ID 목록이다")
    void scheduledTransferCancel() throws Exception {
        ScheduledTransferCancelRequest request = new ScheduledTransferCancelRequest(List.of(3L, 1L, 2L, 1L));

        String fingerprint = fingerprintOf(
                post("/scheduled-transfers/cancel")
                        .header("Account-Password-Auth-Token", "ACC")
                        .header("Otp-Auth-Token", "OTP"),
                "POST /scheduled-transfers/cancel",
                request);

        assertThat(fingerprint).isEqualTo("{\"customerId\":408,\"scheduledTransferIds\":[1,2,3]}");
    }

    private String fingerprintOf(MockHttpServletRequestBuilder request, String endpoint, Object requestBody)
            throws Exception {
        when(idempotencyService.begin(any(), any(), any(), any()))
                .thenReturn(IdempotencyResult.replay((short) 200, REPLAY_SNAPSHOT));

        mockMvc.perform(request.with(authentication(authenticationOf()))
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk());

        ArgumentCaptor<String> fingerprint = ArgumentCaptor.forClass(String.class);
        verify(idempotencyService).begin(any(), eq(CUSTOMER_ID), eq(endpoint), fingerprint.capture());
        return fingerprint.getValue();
    }

    private UsernamePasswordAuthenticationToken authenticationOf() {
        AuthenticatedCustomer customer = new AuthenticatedCustomer(CUSTOMER_ID, "user" + CUSTOMER_ID, "테스터");
        return UsernamePasswordAuthenticationToken.authenticated(
                customer, null, AuthorityUtils.createAuthorityList("ROLE_CUSTOMER"));
    }
}
