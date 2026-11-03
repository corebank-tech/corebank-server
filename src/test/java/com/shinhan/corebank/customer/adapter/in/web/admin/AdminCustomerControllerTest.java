package com.shinhan.corebank.customer.adapter.in.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shinhan.corebank.AdminAccountFixture;
import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.common.audit.AccessReason;
import com.shinhan.corebank.customer.api.AdminPermission;
import com.shinhan.corebank.customer.domain.model.CustomerStatus;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// 컨트롤러 계약(마스킹·페이징·오류코드·멱등키)을 실제 서비스·MySQL로 본다. 로그인까지 잇는 DoD는
// AdminCustomerAccountOperationIntegrationTest가 맡는다. 두 클래스는 같은 설정이라 컨텍스트를 공유한다.
@AutoConfigureMockMvc
@Transactional
@DisplayName("관리자 고객 계정 운영 API")
class AdminCustomerControllerTest extends IntegrationTestSupport {

    static final long ADMIN_ID = 944900001L;
    private static final String VIEWER_IP = "10.0.0.7"; // MockMvc 기본 원격 주소(127.0.0.1)와 달라야 IP 매핑이 드러난다

    @Autowired
    MockMvc mockMvc;

    @Autowired
    EntityManager entityManager;

    @Autowired
    ObjectMapper objectMapper;

    private Long lockedId;
    private Long otherId;

    @BeforeEach
    void setUp() {
        insertCustomer(ADMIN_ID, "adm449admin", "관리자", "admin@adm449.test", 0, false);
        AdminAccountFixture.grantAdmin(
                entityManager, ADMIN_ID, AdminPermission.CUSTOMER_READ, AdminPermission.CUSTOMER_WRITE);
        lockedId = insertCustomer(null, "adm449locked", "홍길동", "locked@adm449.test", 5, true);
        otherId = insertCustomer(null, "adm449other", "김철수", "other@adm449.test", 2, false);
    }

    @Test
    @DisplayName("검색 결과는 개인정보를 가리고 §6-5 페이징 형식으로 내려준다")
    void searchReturnsMaskedPage() throws Exception {
        mockMvc.perform(get("/admin/customers").param("userId", "adm449lo").with(adminWithReason()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.items[0].customerId").value(lockedId))
                .andExpect(jsonPath("$.data.items[0].userId").value("adm4********"))
                .andExpect(jsonPath("$.data.items[0].userName").value("홍*동"))
                .andExpect(jsonPath("$.data.items[0].email").value("lock**@adm449.test"))
                .andExpect(jsonPath("$.data.items[0].accountLocked").value(true))
                .andExpect(jsonPath("$.data.items[0].joinedAt").value("2026-09-01T09:00:00+09:00"))
                .andExpect(jsonPath("$.data.items[0].birthDate").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].phoneNumber").doesNotExist())
                .andExpect(content().string(not(containsString("adm449locked"))))
                .andExpect(content().string(not(containsString("locked@adm449.test"))));
    }

    @Test
    @DisplayName("잠금 여부만으로 검색할 수 있다")
    void searchByLockStateOnly() throws Exception {
        mockMvc.perform(get("/admin/customers")
                        .param("accountLocked", "true")
                        .param("userId", "adm449")
                        .with(adminWithReason()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].customerId").value(lockedId));
    }

    @Test
    @DisplayName("조건 없는 검색은 CMN0002, 지원하지 않는 페이지 크기는 CMN0005다")
    void rejectsInvalidSearch() throws Exception {
        mockMvc.perform(get("/admin/customers").with(adminWithReason()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0002"));
        mockMvc.perform(get("/admin/customers")
                        .param("userId", "adm449")
                        .param("size", "7")
                        .with(adminWithReason()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0005"));
    }

    @Test
    @DisplayName("상세는 생년월일·연락처까지 가려서 내려주고, 없는 고객은 ATH0201이다")
    void detailIsMaskedOrNotFound() throws Exception {
        mockMvc.perform(get("/admin/customers/{id}", lockedId).with(adminWithReason()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.birthDate").value("1990-**-**"))
                .andExpect(jsonPath("$.data.phoneNumber").value("010****5678"))
                .andExpect(jsonPath("$.data.loginFailureCount").value(5));
        mockMvc.perform(get("/admin/customers/{id}", 999_999_999L).with(adminWithReason()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ATH0201"));
    }

    @Test
    @DisplayName("잠금 해제는 멱등키가 없으면 CMN0002다")
    void unlockRequiresIdempotencyKey() throws Exception {
        mockMvc.perform(post("/admin/customers/{id}/unlock", lockedId)
                        .with(adminWithReason())
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0002"));
    }

    @Test
    @DisplayName("같은 멱등키로 다른 고객을 해제하면 첫 응답을 재생하지 않고 CMN0302로 거부한다")
    void rejectsSameIdempotencyKeyForDifferentTarget() throws Exception {
        String key = UUID.randomUUID().toString();

        mockMvc.perform(post("/admin/customers/{id}/unlock", lockedId)
                        .header("Idempotency-Key", key)
                        .with(adminWithReason())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerId").value(lockedId))
                .andExpect(jsonPath("$.data.accountLocked").value(false));

        mockMvc.perform(post("/admin/customers/{id}/unlock", otherId)
                        .header("Idempotency-Key", key)
                        .with(adminWithReason())
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CMN0302"));
    }

    @Test
    @DisplayName("초기화는 멱등키 없이 200이고 임시 비밀번호를 한 번 돌려준다")
    void resetPasswordWithoutIdempotencyKey() throws Exception {
        mockMvc.perform(post("/admin/customers/{id}/password-reset", lockedId)
                        .with(adminWithReason())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("로그인 비밀번호가 초기화되었습니다."))
                .andExpect(jsonPath("$.data.temporaryPassword").isString())
                .andExpect(jsonPath("$.data.accountLocked").value(false))
                .andExpect(jsonPath("$.data.loginFailureCount").value(0));
    }

    @Test
    @DisplayName("관리자가 자기 자신을 대상으로 하면 CMN0102다")
    void rejectsSelfTarget() throws Exception {
        mockMvc.perform(post("/admin/customers/{id}/password-reset", ADMIN_ID)
                        .with(adminWithReason())
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CMN0102"));
    }

    @Test
    @DisplayName("상태를 정지로 바꾸면 상세·검색 응답에 상태가 반영된다")
    void changeStatusIsReflectedInDetailAndSearch() throws Exception {
        mockMvc.perform(post("/admin/customers/{id}/status", otherId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}")
                        .with(adminWithReason())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("계정 상태가 변경되었습니다."))
                .andExpect(jsonPath("$.data.customerId").value(otherId))
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));

        mockMvc.perform(get("/admin/customers/{id}", otherId).with(adminWithReason()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));
        mockMvc.perform(get("/admin/customers")
                        .param("userId", "adm449")
                        .param("status", "SUSPENDED")
                        .with(adminWithReason()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].customerId").value(otherId))
                .andExpect(jsonPath("$.data.items[0].status").value("SUSPENDED"));
    }

    @Test
    @DisplayName("정의되지 않거나 빠진 상태는 CMN0001, 멱등키가 없으면 CMN0002다")
    void rejectsInvalidStatusChange() throws Exception {
        for (String body : new String[] {"{\"status\":\"DORMANT\"}", "{}"}) {
            mockMvc.perform(post("/admin/customers/{id}/status", otherId)
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .with(adminWithReason())
                            .with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CMN0001"));
        }
        mockMvc.perform(post("/admin/customers/{id}/status", otherId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}")
                        .with(adminWithReason())
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0002"));
    }

    // 본문 status는 CMN9999를 피하려고 문자열 @Pattern으로 받는다. enum에 값이 늘면 패턴도 함께 늘려야 하므로 여기서 묶는다.
    @Test
    @DisplayName("CustomerStatus의 모든 값은 상태 변경 요청 검증을 통과한다")
    void acceptsEveryCustomerStatusValue() throws Exception {
        for (CustomerStatus status : CustomerStatus.values()) {
            mockMvc.perform(post("/admin/customers/{id}/status", otherId)
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"%s\"}".formatted(status.name()))
                            .with(adminWithReason())
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value(status.name()));
        }
    }

    @Test
    @DisplayName("같은 멱등키로 다른 상태를 보내면 첫 응답을 재생하지 않고 CMN0302로 거부한다")
    void rejectsSameIdempotencyKeyForDifferentStatus() throws Exception {
        String key = UUID.randomUUID().toString();

        mockMvc.perform(post("/admin/customers/{id}/status", otherId)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}")
                        .with(adminWithReason())
                        .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/admin/customers/{id}/status", otherId)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}")
                        .with(adminWithReason())
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CMN0302"));
    }

    @Test
    @DisplayName("status=ACTIVE 단독 검색은 CMN0002, 정의되지 않은 status 조건은 CMN0001이다")
    void rejectsInvalidStatusSearch() throws Exception {
        mockMvc.perform(get("/admin/customers").param("status", "ACTIVE").with(adminWithReason()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0002"));
        mockMvc.perform(get("/admin/customers")
                        .param("userId", "adm449")
                        .param("status", "DORMANT")
                        .with(adminWithReason()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"));
    }

    @Test
    @DisplayName("조회 사유 헤더가 없거나 정의되지 않은 값이면 CMN0007이고 조회 감사를 남기지 않는다")
    void rejectsMissingOrUndefinedAccessReason() throws Exception {
        mockMvc.perform(get("/admin/customers").param("userId", "adm449lo").with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0007"));
        mockMvc.perform(get("/admin/customers/{id}", lockedId)
                        .header(AccessReason.HEADER, "FOO")
                        .with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0007"));

        assertThat(adminAuditCount("CUSTOMER_SEARCH")).isZero();
        assertThat(adminAuditCount("CUSTOMER_DETAIL_VIEW")).isZero();
    }

    @Test
    @DisplayName("변경 API도 조회 사유가 없으면 CMN0007이고 대상 계정은 바뀌지 않는다")
    void commandRejectsMissingAccessReason() throws Exception {
        mockMvc.perform(post("/admin/customers/{id}/unlock", lockedId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(admin())
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0007"));

        mockMvc.perform(get("/admin/customers/{id}", lockedId).with(adminWithReason()))
                .andExpect(jsonPath("$.data.accountLocked").value(true));
    }

    @Test
    @DisplayName("상세·상태 변경·비밀번호 초기화도 조회 사유가 없으면 CMN0007이다")
    void otherEndpointsRejectMissingAccessReason() throws Exception {
        mockMvc.perform(get("/admin/customers/{id}", lockedId).with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0007"));
        mockMvc.perform(post("/admin/customers/{id}/status", otherId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}")
                        .with(admin())
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0007"));
        mockMvc.perform(post("/admin/customers/{id}/password-reset", lockedId)
                        .with(admin())
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0007"));
    }

    @Test
    @DisplayName("검색은 관리자·IP·사유·노출된 고객 ID·쓴 조건을 조회 감사 1행으로 남기고 검색어 값은 남기지 않는다")
    void searchRecordsViewAuditWithoutSearchTerm() throws Exception {
        mockMvc.perform(get("/admin/customers")
                        .param("userId", "adm449lo")
                        .param("userName", "홍길")
                        .param("email", "locked@adm449.test")
                        .param("accountLocked", "true")
                        .param("status", "ACTIVE")
                        .header(AccessReason.HEADER, "AUDIT")
                        .remoteAddress(VIEWER_IP)
                        .with(admin()))
                .andExpect(status().isOk());

        AuditRow row = adminAuditRow("CUSTOMER_SEARCH");
        assertThat(row.accessReason()).isEqualTo("AUDIT");
        assertThat(row.requestIp()).isEqualTo(VIEWER_IP);
        assertThat(row.detail().get("customerIds").values())
                .extracting(JsonNode::asLong)
                .containsExactly(lockedId);
        assertThat(row.detail().get("conditions").values())
                .extracting(JsonNode::asString)
                .containsExactly("userId", "userName", "email", "accountLocked", "status");
        assertThat(row.detail().get("resultCount").asInt()).isEqualTo(1);
        assertThat(row.rawDetail())
                .doesNotContain("adm449lo")
                .doesNotContain("홍길")
                .doesNotContain("locked@adm449.test");
    }

    @Test
    @DisplayName("상세 조회는 대상 고객 ID와 사유를 조회 감사 1행으로 남긴다")
    void detailRecordsViewAudit() throws Exception {
        mockMvc.perform(get("/admin/customers/{id}", lockedId)
                        .header(AccessReason.HEADER, "INCIDENT")
                        .with(admin()))
                .andExpect(status().isOk());

        AuditRow row = adminAuditRow("CUSTOMER_DETAIL_VIEW");
        assertThat(row.accessReason()).isEqualTo("INCIDENT");
        assertThat(row.detail().get("targetCustomerId").asLong()).isEqualTo(lockedId);
    }

    private AuditRow adminAuditRow(String eventType) {
        entityManager.flush();
        Object[] row = (Object[]) entityManager
                .createNativeQuery("SELECT access_reason, request_ip, CAST(detail AS CHAR) FROM audit_log "
                        + "WHERE customer_id = :adminId AND event_type = :eventType")
                .setParameter("adminId", ADMIN_ID)
                .setParameter("eventType", eventType)
                .getSingleResult();
        String rawDetail = (String) row[2];
        return new AuditRow((String) row[0], (String) row[1], rawDetail, objectMapper.readTree(rawDetail));
    }

    private record AuditRow(String accessReason, String requestIp, String rawDetail, JsonNode detail) {}

    private long adminAuditCount(String eventType) {
        entityManager.flush();
        return ((Number) entityManager
                        .createNativeQuery("SELECT COUNT(*) FROM audit_log WHERE customer_id = :adminId "
                                + "AND event_type = :eventType")
                        .setParameter("adminId", ADMIN_ID)
                        .setParameter("eventType", eventType)
                        .getSingleResult())
                .longValue();
    }

    // 조회 사유 헤더(PH-89)까지 붙인 관리자 요청. 사유 누락을 보는 테스트만 admin()을 쓴다.
    private RequestPostProcessor adminWithReason() {
        RequestPostProcessor authenticated = admin();
        return request -> {
            request.addHeader(AccessReason.HEADER, AccessReason.OPERATION.name());
            return authenticated.postProcessRequest(request);
        };
    }

    private RequestPostProcessor admin() {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedCustomer(ADMIN_ID, "adm449admin", "관리자"),
                null,
                AuthorityUtils.createAuthorityList(
                        "ROLE_ADMIN", AdminPermission.CUSTOMER_READ.name(), AdminPermission.CUSTOMER_WRITE.name())));
    }

    private Long insertCustomer(
            Long customerId, String userId, String userName, String email, int failures, boolean locked) {
        LocalDateTime joinedAt = LocalDateTime.of(2026, 9, 1, 9, 0);
        entityManager
                .createNativeQuery("INSERT INTO customer (customer_id, user_id, password_hash, user_name, birth_date, "
                        + "email, phone_number, login_failure_count, account_locked, joined_at, created_at, updated_at) "
                        + "VALUES (:customerId, :userId, '$2a$10$adm449hash', :userName, '1990-01-01', :email, "
                        + "'01012345678', :failures, :locked, :joinedAt, :joinedAt, :joinedAt)")
                .setParameter("customerId", customerId)
                .setParameter("userId", userId)
                .setParameter("userName", userName)
                .setParameter("email", email)
                .setParameter("failures", failures)
                .setParameter("locked", locked)
                .setParameter("joinedAt", joinedAt)
                .executeUpdate();
        return customerId != null
                ? customerId
                : ((Number) entityManager
                                .createNativeQuery("SELECT LAST_INSERT_ID()")
                                .getSingleResult())
                        .longValue();
    }
}
