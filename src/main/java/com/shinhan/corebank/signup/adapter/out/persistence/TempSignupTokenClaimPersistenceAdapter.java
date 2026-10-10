package com.shinhan.corebank.signup.adapter.out.persistence;

import com.shinhan.corebank.common.authtoken.AuthTokenPurpose;
import com.shinhan.corebank.common.authtoken.AuthTokenStore;
import com.shinhan.corebank.common.ephemeralstore.ConditionalOnJdbcEphemeralStore;
import com.shinhan.corebank.signup.application.port.out.TempSignupTokenClaimPort;
import com.shinhan.corebank.signup.domain.model.TempSignupTokenPayload;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// tempSignupToken을 claim_id로 선점하고, 가입 결과에 따라 소비하거나 선점을 풀어 되돌린다.
@Component
@ConditionalOnJdbcEphemeralStore
@RequiredArgsConstructor
public class TempSignupTokenClaimPersistenceAdapter implements TempSignupTokenClaimPort {

    private static final AuthTokenPurpose PURPOSE = AuthTokenPurpose.TEMP_SIGNUP;

    private final AuthTokenStore authTokenStore;

    @Override
    public Optional<TempSignupTokenPayload> claim(String token, String claimId) {
        return authTokenStore.claim(PURPOSE, token, claimId, TempSignupTokenPayload.class);
    }

    @Override
    public void complete(String token, String claimId) {
        if (!authTokenStore.completeClaim(PURPOSE, token, claimId)) {
            throw new IllegalStateException("임시 회원가입 토큰 선점 완료에 실패했습니다.");
        }
    }

    @Override
    public void release(String token, String claimId) {
        if (!authTokenStore.releaseClaim(PURPOSE, token, claimId)) {
            throw new IllegalStateException("임시 회원가입 토큰 선점 복구에 실패했습니다.");
        }
    }
}
