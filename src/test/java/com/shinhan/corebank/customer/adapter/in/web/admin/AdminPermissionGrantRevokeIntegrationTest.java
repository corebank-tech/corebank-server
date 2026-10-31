package com.shinhan.corebank.customer.adapter.in.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shinhan.corebank.AdminAccountFixture;
import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.customer.api.AdminPermission;
import jakarta.persistence.EntityManager;
import java.util.List;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

// 부여·회수 API가 인가·멱등키·감사·세션 종료·DB 제약까지 실제 MySQL 스키마에서 이어지는지 본다.
// 테스트 ID는 다른 관리자 테스트(944900xxx)와 겹치지 않는 대역을 쓴다.
@AutoConfigureMockMvc
@Transactional
@DisplayName("관리자 권한 부여·회수 API")
class AdminPermissionGrantRevokeIntegrationTest extends IntegrationTestSupport {

    private static final long GRANTOR_ID = 944910001L;
    private static final long TARGET_ADMIN_ID = 944910002L;
    private static final long PLAIN_CUSTOMER_ID = 944910003L;
    private static final long READER_ID = 944910004L;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    EntityManager entityManager;

    @BeforeEach
    void setUp() {
        AdminAccountFixture.createAdmin(
                entityManager, GRANTOR_ID, AdminPermission.CUSTOMER_READ, AdminPermission.CUSTOMER_WRITE);
        AdminAccountFixture.createAdmin(entityManager, TARGET_ADMIN_ID);
        AdminAccountFixture.createAdmin(entityManager, READER_ID, AdminPermission.CUSTOMER_READ);
        insertPlainCustomer(PLAIN_CUSTOMER_ID);
    }

    @Test
    @DisplayName("관리자에게 권한을 부여하면 권한 행과 감사 1행이 남고 변경 후 권한 전부를 돌려준다")
    void grantPersistsPermissionAndAudit() throws Exception {
        mockMvc.perform(grant(TARGET_ADMIN_ID, "GL_READ", UUID.randomUUID().toString())
                        .with(grantor())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.customerId").value(TARGET_ADMIN_ID))
                .andExpect(jsonPath("$.data.permissions[0]").value("GL_READ"));

        entityManager.flush();
        entityManager.clear();
        assertThat(permissionsOf(TARGET_ADMIN_ID)).containsExactly("GL_READ");
        assertThat(auditRows("ADMIN_PERMISSION_GRANT", "SUCCESS", TARGET_ADMIN_ID))
                .isEqualTo(1);
        assertThat(auditDetail("ADMIN_PERMISSION_GRANT"))
                .contains("\"permission\": \"GL_READ\"")
                .contains("\"granted\": true");
    }

    @Test
    @DisplayName("이미 가진 권한을 다시 부여해도 200이고 행은 하나다")
    void grantTwiceKeepsSingleRow() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(grant(TARGET_ADMIN_ID, "GL_READ", UUID.randomUUID().toString())
                            .with(grantor())
                            .with(csrf()))
                    .andExpect(status().isOk());
        }

        entityManager.flush();
        entityManager.clear();
        assertThat(permissionsOf(TARGET_ADMIN_ID)).containsExactly("GL_READ");
    }

    @Test
    @DisplayName("조회 전용 관리자가 부여·회수를 호출하면 CMN0102로 거부된다")
    void readOnlyAdminIsRejected() throws Exception {
        mockMvc.perform(grant(TARGET_ADMIN_ID, "GL_READ", UUID.randomUUID().toString())
                        .with(reader())
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CMN0102"));
        mockMvc.perform(delete("/admin/customers/{id}/permissions/{permission}", TARGET_ADMIN_ID, "GL_READ")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(reader())
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CMN0102"));

        assertThat(permissionsOf(TARGET_ADMIN_ID)).isEmpty();
    }

    @Test
    @DisplayName("멱등키가 없으면 CMN0002이고, 같은 키로 다른 권한을 주면 CMN0302다")
    void enforcesIdempotencyKey() throws Exception {
        mockMvc.perform(post("/admin/customers/{id}/permissions", TARGET_ADMIN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permission\":\"GL_READ\"}")
                        .with(grantor())
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0002"));

        String key = UUID.randomUUID().toString();
        mockMvc.perform(grant(TARGET_ADMIN_ID, "GL_READ", key).with(grantor()).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(grant(TARGET_ADMIN_ID, "GL_WRITE", key).with(grantor()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CMN0302"));
    }

    @Test
    @DisplayName("정의되지 않은 권한은 CMN0001이다")
    void rejectsUnknownPermission() throws Exception {
        mockMvc.perform(grant(TARGET_ADMIN_ID, "gl_read", UUID.randomUUID().toString())
                        .with(grantor())
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"));
        mockMvc.perform(delete("/admin/customers/{id}/permissions/{permission}", TARGET_ADMIN_ID, "GL_RAED")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(grantor())
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"));
    }

    @Test
    @DisplayName("관리자가 아닌 고객에게는 부여할 수 없고 자기 자신에게도 부여할 수 없다")
    void rejectsNonAdminAndSelfTarget() throws Exception {
        mockMvc.perform(grant(PLAIN_CUSTOMER_ID, "GL_READ", UUID.randomUUID().toString())
                        .with(grantor())
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0001"));
        mockMvc.perform(grant(GRANTOR_ID, "GL_READ", UUID.randomUUID().toString())
                        .with(grantor())
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CMN0102"));

        entityManager.flush();
        entityManager.clear();
        assertThat(permissionsOf(PLAIN_CUSTOMER_ID)).isEmpty();
    }

    @Test
    @DisplayName("권한을 회수하면 행이 지워지고 대상 관리자의 로그인 세션도 함께 사라진다")
    void revokeDeletesPermissionAndSessions() throws Exception {
        AdminAccountFixture.grantAdmin(entityManager, TARGET_ADMIN_ID, AdminPermission.GL_READ);
        String targetSessionId = insertSession(TARGET_ADMIN_ID);
        String grantorSessionId = insertSession(GRANTOR_ID);

        mockMvc.perform(delete("/admin/customers/{id}/permissions/{permission}", TARGET_ADMIN_ID, "GL_READ")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(grantor())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.permissions").isEmpty());

        entityManager.flush();
        entityManager.clear();
        assertThat(permissionsOf(TARGET_ADMIN_ID)).isEmpty();
        assertThat(sessionExists(targetSessionId)).isFalse();
        assertThat(sessionExists(grantorSessionId)).isTrue();
        assertThat(auditRows("ADMIN_PERMISSION_REVOKE", "SUCCESS", TARGET_ADMIN_ID))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("가진 적 없는 권한을 회수하면 200이고 세션은 그대로다")
    void revokeOfAbsentPermissionKeepsSessions() throws Exception {
        String targetSessionId = insertSession(TARGET_ADMIN_ID);

        mockMvc.perform(delete("/admin/customers/{id}/permissions/{permission}", TARGET_ADMIN_ID, "GL_READ")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(grantor())
                        .with(csrf()))
                .andExpect(status().isOk());

        assertThat(sessionExists(targetSessionId)).isTrue();
    }

    @Test
    @DisplayName("role이 CUSTOMER인 고객에 권한 행을 넣으면 DB가 복합 FK로 막는다")
    void databaseRejectsPermissionForCustomerRole() {
        assertThatThrownBy(() -> entityManager
                        .createNativeQuery("INSERT INTO admin_permission (customer_id, permission, granted_at) "
                                + "VALUES (:customerId, 'GL_READ', NOW(6))")
                        .setParameter("customerId", PLAIN_CUSTOMER_ID)
                        .executeUpdate())
                .hasStackTraceContaining("fk_admin_permission_admin");
    }

    @Test
    @DisplayName("권한 행이 남은 관리자를 CUSTOMER로 바꾸려 하면 DB가 막는다")
    void databaseRejectsDowngradeWhilePermissionsRemain() {
        AdminAccountFixture.grantAdmin(entityManager, TARGET_ADMIN_ID, AdminPermission.GL_READ);

        assertThatThrownBy(() -> entityManager
                        .createNativeQuery("UPDATE customer SET role = 'CUSTOMER' WHERE customer_id = :customerId")
                        .setParameter("customerId", TARGET_ADMIN_ID)
                        .executeUpdate())
                .hasStackTraceContaining("fk_admin_permission_admin");
    }

    private MockHttpServletRequestBuilder grant(long targetId, String permission, String idempotencyKey) {
        return post("/admin/customers/{id}/permissions", targetId)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permission\":\"" + permission + "\"}");
    }

    private RequestPostProcessor grantor() {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedCustomer(GRANTOR_ID, "adm" + GRANTOR_ID, "관리자"),
                null,
                AuthorityUtils.createAuthorityList(
                        "ROLE_ADMIN", AdminPermission.CUSTOMER_READ.name(), AdminPermission.CUSTOMER_WRITE.name())));
    }

    private RequestPostProcessor reader() {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedCustomer(READER_ID, "adm" + READER_ID, "관리자"),
                null,
                AuthorityUtils.createAuthorityList("ROLE_ADMIN", AdminPermission.CUSTOMER_READ.name())));
    }

    private void insertPlainCustomer(long customerId) {
        entityManager
                .createNativeQuery("INSERT INTO customer (customer_id, user_id, password_hash, user_name, "
                        + "birth_date, email, phone_number, login_failure_count, account_locked, "
                        + "joined_at, created_at, updated_at) "
                        + "VALUES (:customerId, :userId, 'not-a-login-test', '고객', '1990-01-01', "
                        + ":email, '01012345678', 0, false, NOW(6), NOW(6), NOW(6))")
                .setParameter("customerId", customerId)
                .setParameter("userId", "cus" + customerId)
                .setParameter("email", "cus" + customerId + "@example.test")
                .executeUpdate();
    }

    private String insertSession(long customerId) {
        String sessionId = UUID.randomUUID().toString();
        entityManager
                .createNativeQuery("INSERT INTO SPRING_SESSION (PRIMARY_ID, SESSION_ID, CREATION_TIME, "
                        + "LAST_ACCESS_TIME, MAX_INACTIVE_INTERVAL, EXPIRY_TIME, PRINCIPAL_NAME) "
                        + "VALUES (:id, :id, 0, 0, 1800, 4102444800000, :principal)")
                .setParameter("id", sessionId)
                .setParameter("principal", String.valueOf(customerId))
                .executeUpdate();
        return sessionId;
    }

    @SuppressWarnings("unchecked")
    private List<String> permissionsOf(long customerId) {
        return entityManager
                .createNativeQuery("SELECT permission FROM admin_permission WHERE customer_id = :customerId "
                        + "ORDER BY permission")
                .setParameter("customerId", customerId)
                .getResultList();
    }

    private boolean sessionExists(String sessionId) {
        return ((Number) entityManager
                                .createNativeQuery("SELECT COUNT(*) FROM SPRING_SESSION WHERE PRIMARY_ID = :id")
                                .setParameter("id", sessionId)
                                .getSingleResult())
                        .longValue()
                > 0;
    }

    // 감사는 행위자(GRANTOR)로 남고 대상은 detail.targetCustomerId에 들어간다.
    private long auditRows(String eventType, String result, long targetCustomerId) {
        return ((Number) entityManager
                        .createNativeQuery("SELECT COUNT(*) FROM audit_log WHERE customer_id = :actor "
                                + "AND event_type = :eventType AND result = :result "
                                + "AND JSON_EXTRACT(detail, '$.targetCustomerId') = :target")
                        .setParameter("actor", GRANTOR_ID)
                        .setParameter("eventType", eventType)
                        .setParameter("result", result)
                        .setParameter("target", targetCustomerId)
                        .getSingleResult())
                .longValue();
    }

    private String auditDetail(String eventType) {
        return (String) entityManager
                .createNativeQuery("SELECT CAST(detail AS CHAR) FROM audit_log WHERE customer_id = :actor "
                        + "AND event_type = :eventType")
                .setParameter("actor", GRANTOR_ID)
                .setParameter("eventType", eventType)
                .getSingleResult();
    }
}
