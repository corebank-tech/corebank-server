package com.shinhan.corebank.signup.application.port.out;

import com.shinhan.corebank.signup.domain.model.TempSignupTokenPayload;
import java.time.Duration;

// 선행 인증 토큰을 소비하면서 tempSignupToken을 원자적으로 교체한다.
public interface SignupTokenTransitionPort {

    boolean replaceInitialTokensWithTemp(
            String termsAuthToken,
            String accountAuthToken,
            String userIdCheckToken,
            String emailVerificationToken,
            String newTempSignupToken,
            TempSignupTokenPayload payload,
            Duration ttl);

    boolean rotateTempToken(
            String currentTempSignupToken,
            String userIdCheckToken,
            String emailVerificationToken,
            String newTempSignupToken,
            TempSignupTokenPayload payload,
            Duration ttl);
}
