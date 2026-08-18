package com.shinhan.corebank.auth.adapter.in.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CsrfTokenCookieIssuer {

    private final CookieCsrfTokenRepository csrfTokenRepository;
    private final CsrfTokenRequestAttributeHandler csrfTokenRequestHandler;

    public void rotate(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        csrfTokenRepository.saveToken(null, request, response);

        CsrfToken csrfToken = csrfTokenRepository.generateToken(request);
        csrfTokenRepository.saveToken(csrfToken, request, response);

        csrfTokenRequestHandler.handle(request, response, () -> csrfToken);
    }
}
