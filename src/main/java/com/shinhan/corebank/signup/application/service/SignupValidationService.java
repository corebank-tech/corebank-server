package com.shinhan.corebank.signup.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.signup.application.port.in.ValidateSignupCommand;
import com.shinhan.corebank.signup.application.port.in.ValidateSignupResult;
import com.shinhan.corebank.signup.application.port.in.ValidateSignupUseCase;
import com.shinhan.corebank.signup.application.port.out.AccountAuthTokenPort;
import com.shinhan.corebank.signup.application.port.out.AuthTokenGeneratorPort;
import com.shinhan.corebank.signup.application.port.out.EmailVerificationTokenPort;
import com.shinhan.corebank.signup.application.port.out.SignupCustomerAvailabilityPort;
import com.shinhan.corebank.signup.application.port.out.SignupTokenTransitionPort;
import com.shinhan.corebank.signup.application.port.out.TempSignupTokenPort;
import com.shinhan.corebank.signup.application.port.out.TermsAuthTokenPort;
import com.shinhan.corebank.signup.application.port.out.UserIdCheckTokenPort;
import com.shinhan.corebank.signup.config.SignupTokenProperties;
import com.shinhan.corebank.signup.domain.exception.SignupErrorCode;
import com.shinhan.corebank.signup.domain.model.AccountAuthTokenPayload;
import com.shinhan.corebank.signup.domain.model.EmailVerificationPurpose;
import com.shinhan.corebank.signup.domain.model.EmailVerificationTokenPayload;
import com.shinhan.corebank.signup.domain.model.TempSignupTokenPayload;
import com.shinhan.corebank.signup.domain.model.TermsAuthTokenPayload;
import com.shinhan.corebank.signup.domain.model.UserIdCheckTokenPayload;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

// 회원가입 입력과 단계별 인증 토큰을 검증하고 tempSignupToken을 발급·회전한다.
@Service
public class SignupValidationService implements ValidateSignupUseCase {

    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d\\s])\\S+$"
    );
    private static final Pattern USER_ID_PATTERN =
            Pattern.compile("^[a-z][a-z0-9]{5,15}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+"
                    + "@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+$"
    );
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\d{11}$");

    private final TermsAuthTokenPort termsTokenPort;
    private final AccountAuthTokenPort accountTokenPort;
    private final UserIdCheckTokenPort userIdTokenPort;
    private final EmailVerificationTokenPort emailTokenPort;
    private final TempSignupTokenPort tempTokenPort;
    private final SignupTokenTransitionPort transitionPort;
    private final SignupCustomerAvailabilityPort availabilityPort;
    private final AuthTokenGeneratorPort tokenGeneratorPort;
    private final PasswordEncoder passwordEncoder;
    private final SignupTokenProperties tokenProperties;
    private final Clock clock;

    public SignupValidationService(
            TermsAuthTokenPort termsTokenPort,
            AccountAuthTokenPort accountTokenPort,
            UserIdCheckTokenPort userIdTokenPort,
            EmailVerificationTokenPort emailTokenPort,
            TempSignupTokenPort tempTokenPort,
            SignupTokenTransitionPort transitionPort,
            SignupCustomerAvailabilityPort availabilityPort,
            AuthTokenGeneratorPort tokenGeneratorPort,
            PasswordEncoder passwordEncoder,
            SignupTokenProperties tokenProperties,
            Clock clock
    ) {
        this.termsTokenPort = termsTokenPort;
        this.accountTokenPort = accountTokenPort;
        this.userIdTokenPort = userIdTokenPort;
        this.emailTokenPort = emailTokenPort;
        this.tempTokenPort = tempTokenPort;
        this.transitionPort = transitionPort;
        this.availabilityPort = availabilityPort;
        this.tokenGeneratorPort = tokenGeneratorPort;
        this.passwordEncoder = passwordEncoder;
        this.tokenProperties = tokenProperties;
        this.clock = clock;
    }

    @Override
    public ValidateSignupResult validate(ValidateSignupCommand command) {
        validatePassword(command.userPassword(), command.userPasswordConfirm());
        validatePhoneNumber(command.phoneNumber());
        validateRequiredIdentity(command.userId(), command.email());
        validateUserIdFormat(command.userId());
        validateEmailFormat(command.email());

        String normalizedEmail = command.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (command.isEditRequest()) {
            return validateEditedInput(command, normalizedEmail);
        }
        return validateInitialInput(command, normalizedEmail);
    }

    private ValidateSignupResult validateInitialInput(
            ValidateSignupCommand command,
            String normalizedEmail
    ) {
        TermsAuthTokenPayload terms = findTerms(command.termsAuthToken());
        AccountAuthTokenPayload account = findAccount(command.accountAuthToken());
        UserIdCheckTokenPayload userIdProof = findUserId(command.userIdCheckToken());
        EmailVerificationTokenPayload emailProof = findEmail(
                command.emailVerificationToken()
        );

        validateUserIdProof(command.userId(), userIdProof);
        validateEmailProof(normalizedEmail, emailProof);
        validateCurrentAvailability(command.userId(), normalizedEmail);

        TempSignupTokenPayload payload = new TempSignupTokenPayload(
                terms.agreedTerms(),
                account.existingBankCustomerId(),
                account.verifiedBankAccountId(),
                command.userId(),
                passwordEncoder.encode(command.userPassword()),
                normalizedEmail,
                command.phoneNumber(),
                Instant.now(clock)
        );
        String newToken = tokenGeneratorPort.generateTempSignupToken();

        boolean transitioned = transitionPort.replaceInitialTokensWithTemp(
                command.termsAuthToken(),
                command.accountAuthToken(),
                command.userIdCheckToken(),
                command.emailVerificationToken(),
                newToken,
                payload,
                tokenProperties.tempSignupTtl()
        );
        ensureTransitioned(transitioned);
        return result(newToken);
    }

    private ValidateSignupResult validateEditedInput(
            ValidateSignupCommand command,
            String normalizedEmail
    ) {
        TempSignupTokenPayload current = tempTokenPort
                .find(command.tempSignupToken())
                .orElseThrow(this::invalidInput);

        boolean userIdChanged = !current.userId().equals(command.userId());
        boolean emailChanged = !current.email().equals(normalizedEmail);

        String userIdTokenToConsume = null;
        if (userIdChanged) {
            UserIdCheckTokenPayload proof = findUserId(command.userIdCheckToken());
            validateUserIdProof(command.userId(), proof);
            userIdTokenToConsume = command.userIdCheckToken();
        }

        String emailTokenToConsume = null;
        if (emailChanged) {
            EmailVerificationTokenPayload proof = findEmail(
                    command.emailVerificationToken()
            );
            validateEmailProof(normalizedEmail, proof);
            emailTokenToConsume = command.emailVerificationToken();
        }

        validateCurrentAvailability(command.userId(), normalizedEmail);

        TempSignupTokenPayload updated = new TempSignupTokenPayload(
                current.agreedTerms(),
                current.existingBankCustomerId(),
                current.verifiedBankAccountId(),
                command.userId(),
                passwordEncoder.encode(command.userPassword()),
                normalizedEmail,
                command.phoneNumber(),
                Instant.now(clock)
        );
        String newToken = tokenGeneratorPort.generateTempSignupToken();

        boolean transitioned = transitionPort.rotateTempToken(
                command.tempSignupToken(),
                userIdTokenToConsume,
                emailTokenToConsume,
                newToken,
                updated,
                tokenProperties.tempSignupTtl()
        );
        ensureTransitioned(transitioned);
        return result(newToken);
    }

    private TermsAuthTokenPayload findTerms(String token) {
        if (!hasText(token)) {
            throw new BusinessException(SignupErrorCode.INVALID_TERMS_AUTH_TOKEN);
        }
        return termsTokenPort.find(token).orElseThrow(() ->
                new BusinessException(SignupErrorCode.INVALID_TERMS_AUTH_TOKEN)
        );
    }

    private AccountAuthTokenPayload findAccount(String token) {
        if (!hasText(token)) {
            throw new BusinessException(SignupErrorCode.INVALID_ACCOUNT_AUTH_TOKEN);
        }
        return accountTokenPort.find(token).orElseThrow(() ->
                new BusinessException(SignupErrorCode.INVALID_ACCOUNT_AUTH_TOKEN)
        );
    }

    private UserIdCheckTokenPayload findUserId(String token) {
        if (!hasText(token)) {
            throw new BusinessException(SignupErrorCode.USER_ID_CHECK_REQUIRED);
        }
        return userIdTokenPort.find(token).orElseThrow(() ->
                new BusinessException(SignupErrorCode.USER_ID_CHECK_REQUIRED)
        );
    }

    private EmailVerificationTokenPayload findEmail(String token) {
        if (!hasText(token)) {
            throw new BusinessException(
                    SignupErrorCode.INVALID_EMAIL_VERIFICATION_TOKEN
            );
        }
        return emailTokenPort.find(token).orElseThrow(() ->
                new BusinessException(
                        SignupErrorCode.INVALID_EMAIL_VERIFICATION_TOKEN
                )
        );
    }

    private void validatePassword(String password, String confirmation) {
        if (password == null || confirmation == null) {
            throw invalidInput();
        }
        if (!password.equals(confirmation)) {
            throw new BusinessException(
                    SignupErrorCode.PASSWORD_CONFIRMATION_MISMATCH
            );
        }
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            throw new BusinessException(SignupErrorCode.INVALID_PASSWORD_FORMAT);
        }
    }

    private void validatePhoneNumber(String phoneNumber) {
        if (phoneNumber == null
                || !PHONE_PATTERN.matcher(phoneNumber).matches()) {
            throw invalidInput();
        }
    }

    private void validateRequiredIdentity(String userId, String email) {
        if (!hasText(userId) || !hasText(email)) {
            throw invalidInput();
        }
    }

    private void validateUserIdFormat(String userId) {
        if (!USER_ID_PATTERN.matcher(userId).matches()) {
            throw new BusinessException(SignupErrorCode.INVALID_USER_ID_FORMAT);
        }
    }

    private void validateEmailFormat(String email) {
        if (email.length() > 100
                || !email.equals(email.trim())
                || !EMAIL_PATTERN.matcher(email).matches()) {
            throw invalidInput();
        }
    }

    private void validateUserIdProof(
            String requestedUserId,
            UserIdCheckTokenPayload proof
    ) {
        if (!proof.userId().equals(requestedUserId)) {
            throw new BusinessException(SignupErrorCode.USER_ID_CHECK_REQUIRED);
        }
    }

    private void validateEmailProof(
            String requestedEmail,
            EmailVerificationTokenPayload proof
    ) {
        if (proof.purpose() != EmailVerificationPurpose.SIGN_UP
                || !proof.email().equalsIgnoreCase(requestedEmail)) {
            throw new BusinessException(
                    SignupErrorCode.INVALID_EMAIL_VERIFICATION_TOKEN
            );
        }
    }

    private void validateCurrentAvailability(String userId, String email) {
        if (availabilityPort.isUserIdTaken(userId)) {
            throw new BusinessException(SignupErrorCode.DUPLICATE_USER_ID);
        }
        if (availabilityPort.isEmailTaken(email)) {
            throw new BusinessException(SignupErrorCode.DUPLICATE_EMAIL);
        }
    }

    private void ensureTransitioned(boolean transitioned) {
        if (!transitioned) {
            throw invalidInput();
        }
    }

    private ValidateSignupResult result(String token) {
        return new ValidateSignupResult(
                token,
                tokenProperties.tempSignupTtl().toSeconds()
        );
    }

    private BusinessException invalidInput() {
        return new BusinessException(CommonErrorCode.INVALID_INPUT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
