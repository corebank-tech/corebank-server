package com.shinhan.corebank.common.ephemeralstore;

import com.shinhan.corebank.account.application.port.out.AccountPasswordAuthTokenStorePort;
import com.shinhan.corebank.otp.application.port.out.OtpAuthTokenStorePort;
import com.shinhan.corebank.otp.application.port.out.OtpIssueLockPort;
import com.shinhan.corebank.product.application.port.out.TermsViewHistoryPort;
import com.shinhan.corebank.signup.application.port.out.AccountAuthTokenPort;
import com.shinhan.corebank.signup.application.port.out.EmailVerificationTokenPort;
import com.shinhan.corebank.signup.application.port.out.SignupTokenTransitionPort;
import com.shinhan.corebank.signup.application.port.out.TempSignupTokenClaimPort;
import com.shinhan.corebank.signup.application.port.out.TempSignupTokenPort;
import com.shinhan.corebank.signup.application.port.out.TermsAuthTokenPort;
import com.shinhan.corebank.signup.application.port.out.UserIdCheckTokenPort;
import java.util.List;
import java.util.Map;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;

// app.ephemeral-store.provider 스위치 뒤에 있는 포트 11개.
final class EphemeralStorePorts {

    static final List<Class<?>> ALL = List.of(
            AccountPasswordAuthTokenStorePort.class,
            OtpAuthTokenStorePort.class,
            OtpIssueLockPort.class,
            TermsViewHistoryPort.class,
            TermsAuthTokenPort.class,
            UserIdCheckTokenPort.class,
            EmailVerificationTokenPort.class,
            AccountAuthTokenPort.class,
            TempSignupTokenPort.class,
            SignupTokenTransitionPort.class,
            TempSignupTokenClaimPort.class);

    private EphemeralStorePorts() {}

    // 포트마다 빈이 정확히 하나라는 전제에서, 그 구현 클래스 이름을 돌려준다(프록시면 원본).
    static String implementationOf(ApplicationContext context, Class<?> port) {
        Map<String, ?> beans = context.getBeansOfType(port);
        if (beans.size() != 1) {
            throw new AssertionError(port.getSimpleName() + " 구현 빈이 " + beans.size() + "개입니다: " + beans.keySet());
        }
        return AopUtils.getTargetClass(beans.values().iterator().next()).getName();
    }
}
