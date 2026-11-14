package com.shinhan.corebank.fds.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.fds.domain.exception.FdsErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 내부 Agent Bearer 토큰을 세션과 분리해 상수 시간으로 검증한다. */
@Component
public class FdsAgentTokenVerifier {
    private final byte[] expected;

    public FdsAgentTokenVerifier(@Value("${FDS_AGENT_SERVICE_TOKEN:}") String token) {
        expected = ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
        if (token.isBlank()) expected[0] = 0;
    }

    public void verify(String supplied) {
        byte[] actual = supplied == null ? new byte[0] : supplied.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new BusinessException(FdsErrorCode.AGENT_UNAUTHORIZED);
        }
    }
}
