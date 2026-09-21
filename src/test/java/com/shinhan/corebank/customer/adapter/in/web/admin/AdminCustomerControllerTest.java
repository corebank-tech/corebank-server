package com.shinhan.corebank.customer.adapter.in.web.admin;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

// 컨트롤러 계약(마스킹·페이징·오류코드·멱등키)을 실제 서비스·MySQL로 본다. 로그인까지 잇는 DoD는
// AdminCustomerAccountOperationIntegrationTest가 맡는다. 두 클래스는 같은 설정이라 컨텍스트를 공유한다.
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "app.security.admin.bootstrap-customer-ids=" + AdminCustomerControllerTest.ADMIN_ID)
@DisplayName("관리자 고객 계정 운영 API")
class AdminCustomerControllerTest extends IntegrationTestSupport {

    static final long ADMIN_ID = 944900001L;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    EntityManager entityManager;

    private Long lockedId;
    private Long otherId;

    @BeforeEach
    void setUp() {
        insertCustomer(ADMIN_ID, "adm449admin", "관리자", "admin@adm449.test", 0, false);
        lockedId = insertCustomer(null, "adm449locked", "홍길동", "locked@adm449.test", 5, true);
        otherId = insertCustomer(null, "adm449other", "김철수", "other@adm449.test", 2, false);
    }

    @Test
    @DisplayName("검색 결과는 개인정보를 가리고 §6-5 페이징 형식으로 내려준다")
    void searchReturnsMaskedPage() throws Exception {
        mockMvc.perform(get("/admin/customers").param("userId", "adm449lo").with(admin()))
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
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].customerId").value(lockedId));
    }

    @Test
    @DisplayName("조건 없는 검색은 CMN0002, 지원하지 않는 페이지 크기는 CMN0005다")
    void rejectsInvalidSearch() throws Exception {
        mockMvc.perform(get("/admin/customers").with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0002"));
        mockMvc.perform(get("/admin/customers")
                        .param("userId", "adm449")
                        .param("size", "7")
                        .with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0005"));
    }

    @Test
    @DisplayName("상세는 생년월일·연락처까지 가려서 내려주고, 없는 고객은 ATH0201이다")
    void detailIsMaskedOrNotFound() throws Exception {
        mockMvc.perform(get("/admin/customers/{id}", lockedId).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.birthDate").value("1990-**-**"))
                .andExpect(jsonPath("$.data.phoneNumber").value("010****5678"))
                .andExpect(jsonPath("$.data.loginFailureCount").value(5));
        mockMvc.perform(get("/admin/customers/{id}", 999_999_999L).with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ATH0201"));
    }

    @Test
    @DisplayName("잠금 해제는 멱등키가 없으면 CMN0002다")
    void unlockRequiresIdempotencyKey() throws Exception {
        mockMvc.perform(post("/admin/customers/{id}/unlock", lockedId)
                        .with(admin())
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
                        .with(admin())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerId").value(lockedId))
                .andExpect(jsonPath("$.data.accountLocked").value(false));

        mockMvc.perform(post("/admin/customers/{id}/unlock", otherId)
                        .header("Idempotency-Key", key)
                        .with(admin())
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CMN0302"));
    }

    @Test
    @DisplayName("초기화는 멱등키 없이 200이고 임시 비밀번호를 한 번 돌려준다")
    void resetPasswordWithoutIdempotencyKey() throws Exception {
        mockMvc.perform(post("/admin/customers/{id}/password-reset", lockedId)
                        .with(admin())
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
                        .with(admin())
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CMN0102"));
    }

    private RequestPostProcessor admin() {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedCustomer(ADMIN_ID, "adm449admin", "관리자"),
                null,
                AuthorityUtils.createAuthorityList("ROLE_CUSTOMER")));
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
