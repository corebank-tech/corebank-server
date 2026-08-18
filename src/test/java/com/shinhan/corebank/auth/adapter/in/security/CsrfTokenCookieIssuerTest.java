package com.shinhan.corebank.auth.adapter.in.security;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;

@ExtendWith(MockitoExtension.class)
class CsrfTokenCookieIssuerTest {

    @Mock
    CookieCsrfTokenRepository csrfTokenRepository;

    @Mock
    HttpServletRequest request;

    @Mock
    HttpServletResponse response;

    @Mock
    CsrfToken csrfToken;

    @Test
    void removesExistingTokenBeforeIssuingNewToken() {
        given(csrfTokenRepository.generateToken(request))
                .willReturn(csrfToken);
        given(csrfToken.getParameterName()).willReturn("_csrf");

        CsrfTokenCookieIssuer issuer =
                new CsrfTokenCookieIssuer(csrfTokenRepository);

        issuer.rotate(request, response);

        InOrder inOrder = inOrder(csrfTokenRepository);
        inOrder.verify(csrfTokenRepository)
                .saveToken(null, request, response);
        inOrder.verify(csrfTokenRepository).generateToken(request);
        inOrder.verify(csrfTokenRepository)
                .saveToken(csrfToken, request, response);
        verify(request).setAttribute(CsrfToken.class.getName(), csrfToken);
        verify(request).setAttribute("_csrf", csrfToken);
    }
}
