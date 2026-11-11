package com.shinhan.corebank.paymentstop.adapter.in.web.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.support.CustomerTestFixture;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.paymentstop.application.port.in.AdminActor;
import com.shinhan.corebank.paymentstop.application.port.in.RegisterPaymentStopCaseUseCase;
import com.shinhan.corebank.paymentstop.support.PaymentStopTestData;
import com.shinhan.corebank.paymentstop.support.PaymentStopTestData.Scenario;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** 고객 이의제기 API 의 multipart 요청, 멱등키, 존재 여부 비노출 응답. */
@AutoConfigureMockMvc
@DisplayName("고객 지급정지 이의제기 API")
class CustomerObjectionControllerTest extends IntegrationTestSupport {

    private static final byte[] PDF = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegisterPaymentStopCaseUseCase registerUseCase;

    @Autowired
    private CustomerTestFixture customerTestFixture;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    private Long holderId;
    private Long otherId;
    private PaymentStopTestData data;

    @BeforeEach
    void setUp() {
        holderId = customerTestFixture.createCustomer();
        otherId = customerTestFixture.createCustomer();
        data = new PaymentStopTestData(
                jdbcTemplate, holderId, LocalDateTime.now(clock).minusDays(1).withNano(0));
    }

    @AfterEach
    void tearDown() {
        data.cleanup();
        data.cleanupActor(otherId);
        customerTestFixture.deleteCustomer(holderId);
        customerTestFixture.deleteCustomer(otherId);
    }

    @Test
    @DisplayName("multipart 로 신청하면 이의제기 ID를 받고, 같은 멱등키 재요청은 같은 ID를 돌려주며 접수는 하나뿐이다")
    void submitWithIdempotentReplay() throws Exception {
        Scenario s = data.scenario(true);
        long caseId = register(s);
        String key = UUID.randomUUID().toString();

        long first = submit(caseId, holderId, key);
        long replay = submit(caseId, holderId, key);

        assertThat(replay).isEqualTo(first);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM objection WHERE case_id = ?", Long.class, caseId))
                .isEqualTo(1L);
        mockMvc.perform(get("/payment-stops/my").with(customer(holderId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].caseId").value(caseId))
                .andExpect(jsonPath("$.data[0].objectionId").value(first))
                .andExpect(jsonPath("$.data[0].objectionStatus").value("RECEIVED"))
                .andExpect(jsonPath("$.data[0].supplementDue").doesNotExist());
    }

    @Test
    @DisplayName("남의 사건에 신청하면 없는 사건과 같은 404 PST0201이다")
    void otherCustomerGetsNotFound() throws Exception {
        long caseId = register(data.scenario(true));

        for (long target : List.of(caseId, Long.MAX_VALUE)) {
            mockMvc.perform(request(target, otherId, UUID.randomUUID().toString()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PST0201"));
        }
    }

    @Test
    @DisplayName("보완 요청 상태가 아니면 보완 제출은 409 PST0308이다")
    void supplementOutsideRequestIsConflict() throws Exception {
        long objectionId = submit(
                register(data.scenario(true)), holderId, UUID.randomUUID().toString());

        mockMvc.perform(multipart("/objections/{id}/supplements", objectionId)
                        .file(new MockMultipartFile("evidence", "보완.pdf", "application/pdf", PDF))
                        .with(customer(holderId))
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PST0308"));
    }

    private long submit(long caseId, long customerId, String key) throws Exception {
        String body = mockMvc.perform(request(caseId, customerId, key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.objectionId")).longValue();
    }

    private MockMultipartHttpServletRequestBuilder request(long caseId, long customerId, String key) {
        return (MockMultipartHttpServletRequestBuilder) multipart("/payment-stops/{caseId}/objections", caseId)
                .file(new MockMultipartFile("idCopy", "신분증.png", "image/png", PNG))
                .file(new MockMultipartFile("evidence", "계약서.pdf", "application/pdf", PDF))
                .param("clause", "1")
                .param("statement", "판매 대금으로 받은 돈입니다.")
                .param("simplifiedRequested", "false")
                .param("litigationPending", "false")
                .with(customer(customerId))
                .with(csrf())
                .header("Idempotency-Key", key);
    }

    private long register(Scenario s) {
        return registerUseCase.register(
                new RegisterPaymentStopCaseUseCase.Command(
                        s.target(), List.of(s.disputed()), LocalDate.now(clock).plusDays(30)),
                new AdminActor(otherId, "127.0.0.1"));
    }

    private static RequestPostProcessor customer(long customerId) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedCustomer(customerId, "holder" + customerId, "명의인"),
                null,
                AuthorityUtils.createAuthorityList("ROLE_CUSTOMER")));
    }
}
