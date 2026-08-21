package com.shinhan.corebank.signup.application.port.out;

import com.shinhan.corebank.signup.domain.model.TempSignupTokenPayload;
import java.util.Optional;

// tempSignupToken을 완료 처리용으로 선점하고 성공·실패 상태를 정리한다.
public interface TempSignupTokenClaimPort {

    Optional<TempSignupTokenPayload> claim(String token, String claimId);

    void complete(String token, String claimId);

    void release(String token, String claimId);
}
