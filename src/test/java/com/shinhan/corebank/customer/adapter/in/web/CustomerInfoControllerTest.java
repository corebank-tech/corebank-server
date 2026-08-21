package com.shinhan.corebank.customer.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
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
class CustomerInfoControllerTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    EntityManager entityManager;

    @Test
    @DisplayName("로그인 고객의 기본정보를 마스킹해 반환한다")
    void getCustomerInfo_returnsMaskedCustomerInformation() throws Exception {
        Long customerId = insertCustomer();

        mockMvc.perform(get("/customers/me")
                        .with(authentication(authenticationOf(customerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.message").value("정상 처리되었습니다."))
                .andExpect(jsonPath("$.data.customerId").value(customerId))
                .andExpect(jsonPath("$.data.userName").value("홍*동"))
                .andExpect(jsonPath("$.data.userId").value("hong*******"))
                .andExpect(jsonPath("$.data.birthDate").value("1995-**-**"))
                .andExpect(jsonPath("$.data.phoneNumber").value("010****5678"))
                .andExpect(jsonPath("$.data.email").value("newm***@corebank.com"))
                .andExpect(jsonPath("$.data.joinedAt")
                        .value("2025-03-10T09:00:00+09:00"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(content().string(not(containsString("honggildong"))))
                .andExpect(content().string(not(containsString("1995-03-10"))))
                .andExpect(content().string(not(containsString("01012345678"))))
                .andExpect(content().string(not(containsString("newmail@corebank.com"))))
                .andExpect(content().string(not(containsString("secret-password-hash"))));
    }

    @Test
    @DisplayName("인증 없이 고객정보를 조회하면 CMN0101을 반환한다")
    void getCustomerInfo_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/customers/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CMN0101"));
    }

    // 통합 테스트가 조회할 고객을 실제 MySQL 스키마에 저장한다.
    private Long insertCustomer() {
        LocalDateTime joinedAt = LocalDateTime.of(2025, 3, 10, 9, 0);

        entityManager.createNativeQuery(
                        "INSERT INTO customer (user_id, password_hash, user_name, birth_date, email, phone_number, "
                                + "joined_at, created_at, updated_at) "
                                + "VALUES (:userId, 'secret-password-hash', '홍길동', '1995-03-10', :email, "
                                + "'01012345678', :joinedAt, :joinedAt, :joinedAt)")
                .setParameter("userId", "honggildong")
                .setParameter("email", "newmail@corebank.com")
                .setParameter("joinedAt", joinedAt)
                .executeUpdate();

        return ((Number) entityManager.createNativeQuery(
                        "SELECT LAST_INSERT_ID()")
                .getSingleResult()).longValue();
    }

    // 실제 인증 필터가 제공하는 로그인 고객 principal을 구성한다.
    private UsernamePasswordAuthenticationToken authenticationOf(Long customerId) {
        AuthenticatedCustomer customer = new AuthenticatedCustomer(
                customerId,
                "honggildong",
                "홍길동"
        );
        return UsernamePasswordAuthenticationToken.authenticated(
                customer,
                null,
                AuthorityUtils.createAuthorityList("ROLE_CUSTOMER")
        );
    }
}
