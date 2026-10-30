package com.shinhan.corebank.auth.adapter.in.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// CustomerIdMdcFilter가 "@Component 기본 등록 순서상 Security 뒤에서 돈다"는 가정을 전체 앱을
// 실제로 띄워서 확인한다(PR #560 리뷰). @WebMvcTest 슬라이스에서는 Filter 빈이 MockMvc에
// 자동으로 안 꽂혀 늘 실패했다 - 전체 컨텍스트라야 운영과 같은 필터 체인을 본다
@AutoConfigureMockMvc
@Import(CustomerIdMdcSecurityIntegrationTest.CustomerIdEchoController.class)
class CustomerIdMdcSecurityIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("인증된 요청이면 CustomerIdMdcFilter가 채운 마스킹된 customerId를 컨트롤러에서도 그대로 본다")
    void authenticatedRequest_seesMaskedCustomerIdInMdc() throws Exception {
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedCustomer(123456L, "user01", "테스터"),
                null,
                AuthorityUtils.createAuthorityList("ROLE_CUSTOMER"));

        mockMvc.perform(get("/api/v1/test/customer-id-echo")
                        .contextPath("/api/v1")
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(content().string("****56"));
    }

    @RestController
    static class CustomerIdEchoController {
        @GetMapping("/test/customer-id-echo")
        String echo() {
            return MDC.get("customerId");
        }
    }
}
