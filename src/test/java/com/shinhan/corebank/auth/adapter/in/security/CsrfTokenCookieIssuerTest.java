package com.shinhan.corebank.auth.adapter.in.security;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@ExtendWith(MockitoExtension.class)
class CsrfTokenCookieIssuerTest {

    @Mock
    CookieCsrfTokenRepository csrfTokenRepository;

    @Mock
    CsrfTokenRequestAttributeHandler csrfTokenRequestHandler;

    @Mock
    HttpServletRequest request;

    @Mock
    HttpServletResponse response;

    @Mock
    CsrfToken csrfToken;

    @Test
    void removesExistingTokenBeforeIssuingNewToken() {
        given(csrfTokenRepository.generateToken(request)).willReturn(csrfToken);

        CsrfTokenCookieIssuer issuer = new CsrfTokenCookieIssuer(csrfTokenRepository, csrfTokenRequestHandler);

        issuer.rotate(request, response);

        InOrder inOrder = inOrder(csrfTokenRepository, csrfTokenRequestHandler);
        inOrder.verify(csrfTokenRepository).saveToken(null, request, response);
        inOrder.verify(csrfTokenRepository).generateToken(request);
        inOrder.verify(csrfTokenRepository).saveToken(csrfToken, request, response);
        inOrder.verify(csrfTokenRequestHandler)
                .handle(eq(request), eq(response), argThat(tokenSupplier -> tokenSupplier.get() == csrfToken));
    }
}
