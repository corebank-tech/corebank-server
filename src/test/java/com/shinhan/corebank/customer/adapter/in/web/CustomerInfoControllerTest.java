package com.shinhan.corebank.customer.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.customer.application.port.in.UpdateCustomerInfoCommand;
import com.shinhan.corebank.customer.application.port.in.UpdateCustomerInfoResult;
import com.shinhan.corebank.customer.application.port.in.UpdateCustomerInfoUseCase;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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

    @MockitoBean
    UpdateCustomerInfoUseCase updateCustomerInfoUseCase;

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

    @Test
    @DisplayName("로그인 고객의 휴대폰 번호와 이메일을 변경한다")
    void updateCustomerInfo_returnsUpdatedInformation() throws Exception {
        Long customerId = insertCustomer();
        given(updateCustomerInfoUseCase.update(any())).willReturn(
                updateResult(customerId)
        );

        mockMvc.perform(patch("/customers/me")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .contentType("application/json")
                        .content(updateRequestJson("EMAIL_VERIFICATION_token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.message")
                        .value("고객정보가 변경되었습니다."))
                .andExpect(jsonPath("$.data.customerId").value(customerId))
                .andExpect(jsonPath("$.data.phoneNumber")
                        .value("010****4321"))
                .andExpect(jsonPath("$.data.email")
                        .value("newm***@corebank.com"))
                .andExpect(jsonPath("$.data.updatedAt")
                        .value("2026-08-21T16:20:00+09:00"));

        ArgumentCaptor<UpdateCustomerInfoCommand> command =
                ArgumentCaptor.forClass(UpdateCustomerInfoCommand.class);
        verify(updateCustomerInfoUseCase).update(command.capture());
        assertThat(command.getValue().customerId()).isEqualTo(customerId);
        assertThat(command.getValue().phoneNumber())
                .isEqualTo("01087654321");
        assertThat(command.getValue().email())
                .isEqualTo("newmail@corebank.com");
    }

    @Test
    @DisplayName("같은 멱등키와 같은 요청은 최초 응답을 재생한다")
    void updateCustomerInfo_replaysResponseWithoutDuplicateUpdate()
            throws Exception {
        Long customerId = insertCustomer();
        String idempotencyKey = UUID.randomUUID().toString();
        given(updateCustomerInfoUseCase.update(any())).willReturn(
                updateResult(customerId)
        );

        mockMvc.perform(patch("/customers/me")
                        .header("Idempotency-Key", idempotencyKey)
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .contentType("application/json")
                        .content(updateRequestJson("EMAIL_VERIFICATION_same")))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(patch("/customers/me")
                        .header("Idempotency-Key", idempotencyKey)
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .contentType("application/json")
                        .content(updateRequestJson("EMAIL_VERIFICATION_same")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email")
                        .value("newm***@corebank.com"));

        verify(updateCustomerInfoUseCase, times(1)).update(any());
    }

    @Test
    @DisplayName("같은 멱등키에 다른 이메일 인증 토큰을 사용하면 CMN0302이다")
    void updateCustomerInfo_rejectsDifferentVerificationTokenWithSameKey()
            throws Exception {
        Long customerId = insertCustomer();
        String idempotencyKey = UUID.randomUUID().toString();
        given(updateCustomerInfoUseCase.update(any())).willReturn(
                updateResult(customerId)
        );

        mockMvc.perform(patch("/customers/me")
                        .header("Idempotency-Key", idempotencyKey)
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .contentType("application/json")
                        .content(updateRequestJson("EMAIL_VERIFICATION_first")))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(patch("/customers/me")
                        .header("Idempotency-Key", idempotencyKey)
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .contentType("application/json")
                        .content(updateRequestJson("EMAIL_VERIFICATION_second")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CMN0302"));

        verify(updateCustomerInfoUseCase, times(1)).update(any());
    }

    @Test
    @DisplayName("고객정보 변경 요청에 멱등키가 없으면 CMN0002이다")
    void updateCustomerInfo_withoutIdempotencyKey_returnsBadRequest()
            throws Exception {
        Long customerId = insertCustomer();

        mockMvc.perform(patch("/customers/me")
                        .with(authentication(authenticationOf(customerId)))
                        .with(csrf())
                        .contentType("application/json")
                        .content(updateRequestJson("EMAIL_VERIFICATION_token")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN0002"));
    }

    @Test
    @DisplayName("CSRF 토큰 없이 고객정보를 변경하면 차단한다")
    void updateCustomerInfo_withoutCsrf_returnsForbidden() throws Exception {
        Long customerId = insertCustomer();

        mockMvc.perform(patch("/customers/me")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(authentication(authenticationOf(customerId)))
                        .contentType("application/json")
                        .content(updateRequestJson("EMAIL_VERIFICATION_token")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("인증 없이 고객정보를 변경하면 CMN0101이다")
    void updateCustomerInfo_withoutAuthentication_returnsUnauthorized()
            throws Exception {
        mockMvc.perform(patch("/customers/me")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(csrf())
                        .contentType("application/json")
                        .content(updateRequestJson("EMAIL_VERIFICATION_token")))
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

    // 고객정보 변경 컨트롤러 테스트의 application 응답을 생성한다.
    private UpdateCustomerInfoResult updateResult(Long customerId) {
        return new UpdateCustomerInfoResult(
                customerId,
                "010****4321",
                "newm***@corebank.com",
                OffsetDateTime.parse("2026-08-21T16:20:00+09:00")
        );
    }

    // 고객정보 변경 요청 JSON에 테스트용 이메일 인증 토큰을 넣는다.
    private String updateRequestJson(String emailVerificationToken) {
        return """
                {
                  "phoneNumber": "01087654321",
                  "email": "newmail@corebank.com",
                  "emailVerificationToken": "%s"
                }
                """.formatted(emailVerificationToken);
    }
}
