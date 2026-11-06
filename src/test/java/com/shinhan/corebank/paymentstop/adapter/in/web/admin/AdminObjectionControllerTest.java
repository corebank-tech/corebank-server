package com.shinhan.corebank.paymentstop.adapter.in.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.support.CustomerTestFixture;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.common.audit.AccessReason;
import com.shinhan.corebank.customer.api.AdminPermission;
import com.shinhan.corebank.paymentstop.support.PaymentStopTestData;
import com.shinhan.corebank.paymentstop.support.PaymentStopTestData.Scenario;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** 관리자 지급정지 API 의 요청 검증, 멱등키, 응답 모양. 서비스와 DB는 실제로 탄다. */
@AutoConfigureMockMvc
@DisplayName("관리자 지급정지 사건과 이의제기 API")
class AdminObjectionControllerTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerTestFixture customerTestFixture;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    private Long adminId;
    private PaymentStopTestData data;

    @BeforeEach
    void setUp() {
        adminId = customerTestFixture.createCustomer();
        data = new PaymentStopTestData(
                jdbcTemplate, adminId, LocalDateTime.now(clock).minusDays(1).withNano(0));
    }

    @AfterEach
    void tearDown() {
        data.cleanup();
        customerTestFixture.deleteCustomer(adminId);
    }

    @Test
    @DisplayName("사건 등록, 이의제기 접수, 상세, 판정 확정이 HTTP로 끝까지 돌고, 같은 멱등키 재요청은 처음 응답을 그대로 돌려준다")
    void fullFlowWithIdempotentReplay() throws Exception {
        Scenario s = data.scenario(true);
        String registerKey = UUID.randomUUID().toString();
        String registerBody = registerBody(s.target(), s.disputed());

        String registered = mockMvc.perform(post("/admin/payment-stops")
                        .with(admin())
                        .with(csrf())
                        .header("Idempotency-Key", registerKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long caseId = ((Number) JsonPath.read(registered, "$.data.caseId")).longValue();

        mockMvc.perform(post("/admin/payment-stops")
                        .with(admin())
                        .with(csrf())
                        .header("Idempotency-Key", registerKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.caseId").value(caseId));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM payment_stop_case WHERE account_id = ?", Long.class, s.target()))
                .isEqualTo(1L);

        String received = mockMvc.perform(post("/admin/objections")
                        .with(admin())
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiveBody(caseId, 1)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long objectionId = ((Number) JsonPath.read(received, "$.data.objectionId")).longValue();

        mockMvc.perform(get("/admin/objections/{id}", objectionId)
                        .with(admin())
                        .header(AccessReason.HEADER, AccessReason.OPERATION.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.caseId").value(caseId))
                .andExpect(jsonPath("$.data.gate.branch").value("NORMAL_ACCEPT"))
                .andExpect(jsonPath("$.data.gate.allowedResults.ACCEPTED").value(true))
                .andExpect(jsonPath("$.data.gate.allowedResults.PARTIAL").value(false))
                .andExpect(jsonPath("$.data.gate.rules.length()").value(3))
                .andExpect(jsonPath("$.data.disputedTransfers[0].transactionNumber")
                        .value(s.disputed()))
                .andExpect(jsonPath("$.data.decision").doesNotExist());

        String decisionKey = UUID.randomUUID().toString();
        String decisionBody = "{\"result\":\"ACCEPTED\",\"reason\":\"관계 거래 확인\",\"earlyReleaseBasis\":\"관계 거래 3회\"}";
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/admin/objections/{id}/decisions", objectionId)
                            .with(admin())
                            .with(csrf())
                            .header("Idempotency-Key", decisionKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(decisionBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.result").value("ACCEPTED"));
        }
        assertThat(data.status(s.target())).isEqualTo("ACTIVE");
        assertThat(data.auditCount("PAYMENT_STOP_RELEASE")).isEqualTo(1L);
    }

    @Test
    @DisplayName("상태를 바꾸는 요청은 멱등키가 없으면 CMN0002다")
    void stateChangesRequireIdempotencyKey() throws Exception {
        Scenario s = data.scenario(true);

        mockMvc.perform(post("/admin/payment-stops")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(s.target(), s.disputed())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0002"));
        mockMvc.perform(post("/admin/objections")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiveBody(1L, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0002"));
        mockMvc.perform(post("/admin/objections/{id}/decisions", 1L)
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"REJECTED\",\"reason\":\"불수용\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0002"));
        assertThat(data.status(s.target())).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("거래번호 형식, 사유 조항 범위, 판정 결과 값이 틀리면 CMN0001이고 상세는 조회 사유가 없으면 CMN0007이다")
    void rejectsInvalidRequests() throws Exception {
        Scenario s = data.scenario(true);

        mockMvc.perform(post("/admin/payment-stops")
                        .with(admin())
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(s.target(), "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"));
        mockMvc.perform(post("/admin/objections")
                        .with(admin())
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiveBody(1L, 4)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"));
        mockMvc.perform(post("/admin/objections/{id}/decisions", 1L)
                        .with(admin())
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"MAYBE\",\"reason\":\"모름\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"));
        mockMvc.perform(get("/admin/objections/{id}", 1L).with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0007"));
    }

    private String registerBody(long accountId, String transactionNumber) {
        return """
                {"accountId": %d, "disputedTransactionNumbers": ["%s"], "objectionDeadline": "%s"}
                """
                .formatted(accountId, transactionNumber, LocalDate.now(clock).plusDays(30));
    }

    private static String receiveBody(long caseId, int clause) {
        return """
                {"caseId": %d, "clause": %d, "statement": "판매 대금으로 받은 돈입니다.",
                 "simplifiedRequested": false, "idCopyConfirmed": true, "litigationPending": false}
                """
                .formatted(caseId, clause);
    }

    private RequestPostProcessor admin() {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedCustomer(adminId, "pstadmin", "관리자"),
                null,
                AuthorityUtils.createAuthorityList(
                        "ROLE_ADMIN",
                        AdminPermission.PAYMENT_STOP_READ.name(),
                        AdminPermission.PAYMENT_STOP_WRITE.name())));
    }
}
