package com.shinhan.corebank.signup.adapter.out.persistence;

import com.shinhan.corebank.common.authtoken.AuthTokenPurpose;
import com.shinhan.corebank.common.authtoken.AuthTokenStore;
import com.shinhan.corebank.common.authtoken.AuthTokenStore.Source;
import com.shinhan.corebank.common.ephemeralstore.ConditionalOnJdbcEphemeralStore;
import com.shinhan.corebank.signup.application.port.out.SignupTokenTransitionPort;
import com.shinhan.corebank.signup.domain.model.TempSignupTokenPayload;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 선행 인증 토큰 소비와 tempSignupToken 발급을 한 트랜잭션으로 처리한다.
@Component
@ConditionalOnJdbcEphemeralStore
@RequiredArgsConstructor
public class SignupTokenTransitionPersistenceAdapter implements SignupTokenTransitionPort {

    private final AuthTokenStore authTokenStore;

    @Override
    public boolean replaceInitialTokensWithTemp(
            String termsAuthToken,
            String accountAuthToken,
            String userIdCheckToken,
            String emailVerificationToken,
            String newTempSignupToken,
            TempSignupTokenPayload payload,
            Duration ttl) {
        return transition(
                List.of(
                        new Source(AuthTokenPurpose.TERMS_AUTH, termsAuthToken),
                        new Source(AuthTokenPurpose.ACCOUNT_AUTH, accountAuthToken),
                        new Source(AuthTokenPurpose.USER_ID_CHECK, userIdCheckToken),
                        new Source(AuthTokenPurpose.EMAIL_VERIFICATION, emailVerificationToken)),
                newTempSignupToken,
                payload,
                ttl);
    }

    @Override
    public boolean rotateTempToken(
            String currentTempSignupToken,
            String userIdCheckToken,
            String emailVerificationToken,
            String newTempSignupToken,
            TempSignupTokenPayload payload,
            Duration ttl) {
        List<Source> sources = new ArrayList<>();
        sources.add(new Source(AuthTokenPurpose.TEMP_SIGNUP, currentTempSignupToken));
        if (hasText(userIdCheckToken)) {
            sources.add(new Source(AuthTokenPurpose.USER_ID_CHECK, userIdCheckToken));
        }
        if (hasText(emailVerificationToken)) {
            sources.add(new Source(AuthTokenPurpose.EMAIL_VERIFICATION, emailVerificationToken));
        }
        return transition(sources, newTempSignupToken, payload, ttl);
    }

    private boolean transition(
            List<Source> sources, String newTempSignupToken, TempSignupTokenPayload payload, Duration ttl) {
        return authTokenStore.transition(sources, AuthTokenPurpose.TEMP_SIGNUP, newTempSignupToken, payload, ttl);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
