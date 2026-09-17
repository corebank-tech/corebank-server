package com.shinhan.corebank.auth.application.service;

import com.shinhan.corebank.auth.application.port.in.*;
import com.shinhan.corebank.auth.application.port.out.PasswordResetRequestPort;
import com.shinhan.corebank.auth.domain.exception.AuthErrorCode;
import com.shinhan.corebank.auth.domain.model.PasswordResetRequest;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.customer.api.*;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PasswordResetService implements IssuePasswordResetUseCase, ResetPasswordUseCase {
    private static final Duration CODE_TTL = Duration.ofSeconds(180);
    private static final Pattern CODE = Pattern.compile("^\\d{6}$");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PASSWORD = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d\\s])\\S{8,15}$");
    private final CustomerAuthenticationFacade customerFacade;
    private final PasswordResetRequestPort requestPort;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Override
    @Transactional
    public IssuePasswordResetResult issue(IssuePasswordResetCommand command) {
        validateIssue(command);
        String email = command.email().trim().toLowerCase(Locale.ROOT);
        PasswordResetCustomerData customer = customerFacade
                .findPasswordResetCustomer(command.userId().trim())
                .filter(c -> c.customerName().equals(command.customerName().trim()))
                .filter(c -> c.email().equalsIgnoreCase(email))
                .orElseThrow(() -> new BusinessException(AuthErrorCode.USER_NOT_FOUND));
        if (customer.accountLocked()) throw new BusinessException(AuthErrorCode.ACCOUNT_LOCKED);
        requestPort.invalidateActive(customer.customerId());
        String id = "PRR_" + randomToken();
        String code = "%06d".formatted(random.nextInt(1_000_000));
        LocalDateTime now = LocalDateTime.now(clock);
        requestPort.save(PasswordResetRequest.issue(
                id, customer.customerId(), email, passwordEncoder.encode(code), now.plus(CODE_TTL), now));
        return new IssuePasswordResetResult(id, code, CODE_TTL.toSeconds());
    }

    @Override
    @Transactional
    public ResetPasswordResult reset(ResetPasswordCommand command) {
        validateResetRequiredFields(command);
        PasswordResetRequest request = requestPort
                .findByIdForUpdate(command.requestId())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.VERIFICATION_REQUEST_NOT_FOUND));
        if (request.used()) throw new BusinessException(AuthErrorCode.VERIFICATION_REQUEST_NOT_FOUND);
        LocalDateTime now = LocalDateTime.now(clock);
        if (!now.isBefore(request.expiresAt())) throw new BusinessException(AuthErrorCode.VERIFICATION_CODE_EXPIRED);
        if (command.verificationCode() == null
                || !CODE.matcher(command.verificationCode()).matches()
                || !passwordEncoder.matches(command.verificationCode(), request.codeHash()))
            throw new BusinessException(AuthErrorCode.VERIFICATION_CODE_MISMATCH);
        if (command.newPassword() == null
                || !PASSWORD.matcher(command.newPassword()).matches())
            throw new BusinessException(AuthErrorCode.INVALID_PASSWORD_FORMAT);
        if (!command.newPassword().equals(command.newPasswordConfirm()))
            throw new BusinessException(AuthErrorCode.PASSWORD_CONFIRMATION_MISMATCH);
        PasswordResetCustomerData customer = customerFacade.findPasswordResetCustomerById(request.customerId());
        if (customer.accountLocked()) {
            throw new BusinessException(AuthErrorCode.ACCOUNT_LOCKED);
        }
        if (passwordEncoder.matches(command.newPassword(), customer.passwordHash()))
            throw new BusinessException(AuthErrorCode.PREVIOUS_PASSWORD_REUSE);
        ResetCustomerPasswordResult resetResult = customerFacade.resetPassword(new ResetCustomerPasswordCommand(
                customer.customerId(), customer.passwordHash(), passwordEncoder.encode(command.newPassword()), now));
        if (resetResult == ResetCustomerPasswordResult.ACCOUNT_LOCKED) {
            throw new BusinessException(AuthErrorCode.ACCOUNT_LOCKED);
        }
        if (resetResult == ResetCustomerPasswordResult.PASSWORD_CHANGED_CONCURRENTLY) {
            throw new BusinessException(CommonErrorCode.CONCURRENT_MODIFICATION);
        }
        request.use(now);
        requestPort.save(request);
        return new ResetPasswordResult(
                customer.customerId(), now.atZone(clock.getZone()).toOffsetDateTime());
    }

    @Override
    @Transactional(readOnly = true)
    public Long resolveCustomerId(String requestId) {
        return requestPort
                .findById(requestId)
                .map(PasswordResetRequest::customerId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.VERIFICATION_REQUEST_NOT_FOUND));
    }

    private void validateIssue(IssuePasswordResetCommand c) {
        if (c == null || c.userId() == null || c.customerName() == null || c.email() == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
        if (c.userId().isBlank()
                || c.userId().length() < 5
                || c.userId().length() > 20
                || c.customerName().isBlank()
                || c.customerName().length() > 50
                || c.email().length() > 100
                || !EMAIL.matcher(c.email()).matches()) throw new BusinessException(CommonErrorCode.INVALID_INPUT);
    }

    private void validateResetRequiredFields(ResetPasswordCommand command) {
        if (command == null
                || command.requestId() == null
                || command.verificationCode() == null
                || command.newPassword() == null
                || command.newPasswordConfirm() == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
    }

    private String randomToken() {
        byte[] b = new byte[32];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
