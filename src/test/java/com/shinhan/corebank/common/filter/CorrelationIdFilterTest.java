package com.shinhan.corebank.common.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void 헤더가_없으면_UUID를_생성해서_MDC와_응답헤더에_싣는다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] seenCorrelationId = new String[1];
        FilterChain chain = (req, res) -> seenCorrelationId[0] = MDC.get("correlationId");

        filter.doFilter(request, response, chain);

        assertThat(seenCorrelationId[0]).isNotBlank();
        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo(seenCorrelationId[0]);
    }

    @Test
    void 헤더가_유효한_UUID_형식이면_그_값을_그대로_쓴다() throws Exception {
        String validUuid = "3fa85f64-5717-4562-b3fc-2c963f66afa6";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-Id", validUuid);
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] seenCorrelationId = new String[1];
        FilterChain chain = (req, res) -> seenCorrelationId[0] = MDC.get("correlationId");

        filter.doFilter(request, response, chain);

        assertThat(seenCorrelationId[0]).isEqualTo(validUuid);
        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo(validUuid);
    }

    // 클라이언트가 계좌번호 형식 값을 X-Correlation-Id에 실어 보내 응답에 그대로 노출시키려는 시도를
    // 막는다(PR #560 2차 리뷰) - UUID 형식이 아니면 서버가 새 ID로 교체한다
    @Test
    void 헤더가_UUID_형식이_아니면_새_UUID로_교체한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-Id", "123456789012");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] seenCorrelationId = new String[1];
        FilterChain chain = (req, res) -> seenCorrelationId[0] = MDC.get("correlationId");

        filter.doFilter(request, response, chain);

        assertThat(seenCorrelationId[0]).isNotEqualTo("123456789012");
        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo(seenCorrelationId[0]);
    }

    // 16진수엔 숫자(0-9)가 포함돼, 하이픈 위치만 맞으면 세그먼트 안에 계좌번호를 그대로 심어도
    // "유효한 UUID"로 통과한다(PR #560 3차 리뷰) - 형식 검증만으로는 못 막던 사례를 실제로 재현한다
    @Test
    void 헤더가_UUID_형식이지만_마지막_세그먼트에_계좌번호가_그대로_들어있으면_교체한다() throws Exception {
        String uuidShapedAccountNumber = "123e4567-e89b-42d3-a456-123456789012";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-Id", uuidShapedAccountNumber);
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] seenCorrelationId = new String[1];
        FilterChain chain = (req, res) -> seenCorrelationId[0] = MDC.get("correlationId");

        filter.doFilter(request, response, chain);

        assertThat(seenCorrelationId[0]).isNotEqualTo(uuidShapedAccountNumber);
        assertThat(seenCorrelationId[0]).doesNotContainPattern("\\d{11,}");
        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo(seenCorrelationId[0]);
    }

    // 응답과 로그가 항상 같은 값을 보도록 하는 게 이 필터의 핵심 계약이다 - 교체된 값도 예외가 아님을 명시적으로 검증
    @Test
    void 새로_생성한_ID도_응답헤더와_MDC에_동일하게_들어간다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] seenCorrelationId = new String[1];
        FilterChain chain = (req, res) -> seenCorrelationId[0] = MDC.get("correlationId");

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo(seenCorrelationId[0]);
    }

    @Test
    void 필터가_끝나면_MDC를_정리한다() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(MDC.get("correlationId")).isNull();
    }
}
