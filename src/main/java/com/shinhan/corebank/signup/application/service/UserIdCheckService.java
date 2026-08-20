package com.shinhan.corebank.signup.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.signup.application.port.in.CheckUserIdCommand;
import com.shinhan.corebank.signup.application.port.in.CheckUserIdResult;
import com.shinhan.corebank.signup.application.port.in.CheckUserIdUseCase;
import com.shinhan.corebank.signup.application.port.out.AuthTokenGeneratorPort;
import com.shinhan.corebank.signup.application.port.out.SignupCustomerAvailabilityPort;
import com.shinhan.corebank.signup.application.port.out.UserIdCheckTokenPort;
import com.shinhan.corebank.signup.config.SignupTokenProperties;
import com.shinhan.corebank.signup.domain.exception.SignupErrorCode;
import com.shinhan.corebank.signup.domain.model.UserIdCheckTokenPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

// 회원가입 아이디의 형식과 중복을 검사하고 확인 토큰을 발급한다.
@Service
@RequiredArgsConstructor
public class UserIdCheckService implements CheckUserIdUseCase {

    private static final Pattern USER_ID_PATTERN =
            Pattern.compile("^[a-z][a-z0-9]{5,15}$");

    private final SignupCustomerAvailabilityPort customerAvailabilityPort;
    private final UserIdCheckTokenPort userIdCheckTokenPort;
    private final AuthTokenGeneratorPort authTokenGeneratorPort;
    private final SignupTokenProperties tokenProperties;
    private final Clock clock;

    @Override
    public CheckUserIdResult check(CheckUserIdCommand command) {
        String userId = command.userId();

        if (userId == null || !USER_ID_PATTERN.matcher(userId).matches()) {
            throw new BusinessException(SignupErrorCode.INVALID_USER_ID_FORMAT);
        }

        if (customerAvailabilityPort.isUserIdTaken(userId)) {
            throw new BusinessException(SignupErrorCode.DUPLICATE_USER_ID);
        }

        String token = authTokenGeneratorPort.generateUserIdCheckToken();

        // 검증 당시 아이디를 토큰에 묶어 이후 입력값 변경을 막는다.
        userIdCheckTokenPort.save(
                token,
                new UserIdCheckTokenPayload(
                        userId,
                        LocalDateTime.now(clock)
                ),
                tokenProperties.userIdCheckTtl()
        );

        return new CheckUserIdResult(
                true,
                token,
                tokenProperties.userIdCheckTtl().toSeconds()
        );
    }
}
