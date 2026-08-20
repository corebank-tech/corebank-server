package com.shinhan.corebank.signup.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.signup.application.port.in.IssueEmailVerificationCommand;
import com.shinhan.corebank.signup.application.port.in.IssueEmailVerificationResult;
import com.shinhan.corebank.signup.application.port.in.IssueEmailVerificationUseCase;
import com.shinhan.corebank.signup.application.port.in.VerifyEmailCommand;
import com.shinhan.corebank.signup.application.port.in.VerifyEmailResult;
import com.shinhan.corebank.signup.application.port.in.VerifyEmailUseCase;
import com.shinhan.corebank.signup.application.port.out.AuthTokenGeneratorPort;
import com.shinhan.corebank.signup.application.port.out.EmailVerificationCodeGeneratorPort;
import com.shinhan.corebank.signup.application.port.out.EmailVerificationRequestPort;
import com.shinhan.corebank.signup.application.port.out.EmailVerificationTokenPort;
import com.shinhan.corebank.signup.application.port.out.SignupCustomerAvailabilityPort;
import com.shinhan.corebank.signup.application.port.out.VerificationRequestIdGeneratorPort;
import com.shinhan.corebank.signup.config.EmailVerificationProperties;
import com.shinhan.corebank.signup.config.SignupTokenProperties;
import com.shinhan.corebank.signup.domain.exception.SignupErrorCode;
import com.shinhan.corebank.signup.domain.model.EmailVerificationRequest;
import com.shinhan.corebank.signup.domain.model.EmailVerificationTokenPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.regex.Pattern;

// 이메일 인증번호의 발급·재발급 무효화·검증·완료 토큰 발급을 처리한다.
@Service
@RequiredArgsConstructor
public class EmailVerificationService implements
        IssueEmailVerificationUseCase,
        VerifyEmailUseCase {

    private static final Pattern CODE_PATTERN = Pattern.compile("^\\d{6}$");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final SignupCustomerAvailabilityPort customerAvailabilityPort;
    private final EmailVerificationRequestPort requestPort;
    private final EmailVerificationTokenPort tokenPort;
    private final VerificationRequestIdGeneratorPort requestIdGeneratorPort;
    private final EmailVerificationCodeGeneratorPort codeGeneratorPort;
    private final AuthTokenGeneratorPort authTokenGeneratorPort;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationProperties properties;
    private final SignupTokenProperties tokenProperties;
    private final Clock clock;

    @Transactional
    @Override
    public IssueEmailVerificationResult issue(
            IssueEmailVerificationCommand command
    ) {
        if (command.email() == null
                || command.purpose() == null
                || command.email().length() > 100
                || !EMAIL_PATTERN.matcher(command.email()).matches()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        String email = command.email().trim().toLowerCase(Locale.ROOT);

        if (customerAvailabilityPort.isEmailTaken(email)) {
            throw new BusinessException(SignupErrorCode.DUPLICATE_EMAIL);
        }

        // 재발급하면 같은 이메일과 목적의 기존 인증번호를 즉시 무효화한다.
        requestPort.invalidateActive(email, command.purpose());

        String requestId = requestIdGeneratorPort.generateEmailVerificationId();
        String verificationCode = codeGeneratorPort.generate();
        LocalDateTime now = LocalDateTime.now(clock);

        requestPort.save(EmailVerificationRequest.issue(
                requestId,
                command.purpose(),
                email,
                passwordEncoder.encode(verificationCode),
                now.plus(properties.codeTtl()),
                now
        ));

        // Phase 1에서는 실제 메일 발송 대신 인증번호를 응답으로 반환한다.
        return new IssueEmailVerificationResult(
                requestId,
                verificationCode,
                properties.codeTtl().toSeconds()
        );
    }

    @Transactional
    @Override
    public VerifyEmailResult verify(VerifyEmailCommand command) {
        EmailVerificationRequest request = requestPort.findByIdForUpdate(
                        command.emailVerificationId()
                )
                .orElseThrow(() -> new BusinessException(
                        SignupErrorCode.EMAIL_VERIFICATION_REQUEST_NOT_FOUND
                ));

        if (request.used()) {
            throw new BusinessException(
                    SignupErrorCode.EMAIL_VERIFICATION_REQUEST_NOT_FOUND
            );
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (!now.isBefore(request.expiresAt())) {
            throw new BusinessException(SignupErrorCode.EMAIL_VERIFICATION_EXPIRED);
        }

        String code = command.verificationCode();
        if (code == null
                || !CODE_PATTERN.matcher(code).matches()
                || !passwordEncoder.matches(code, request.codeHash())) {
            throw new BusinessException(
                    SignupErrorCode.EMAIL_VERIFICATION_CODE_MISMATCH
            );
        }

        // 비관적 락 안에서 사용 완료 처리하여 동일 요청의 중복 성공을 막는다.
        request.verify(now);
        requestPort.save(request);

        String token = authTokenGeneratorPort.generateEmailVerificationToken();
        tokenPort.save(
                token,
                new EmailVerificationTokenPayload(
                        request.target(),
                        request.purpose(),
                        now
                ),
                tokenProperties.emailVerificationTtl()
        );

        return new VerifyEmailResult(
                token,
                now.atZone(clock.getZone()).toOffsetDateTime()
        );
    }
}
