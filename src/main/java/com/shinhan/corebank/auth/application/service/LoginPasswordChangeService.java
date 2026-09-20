package com.shinhan.corebank.auth.application.service;

import com.shinhan.corebank.auth.application.port.in.ChangeLoginPasswordCommand;
import com.shinhan.corebank.auth.application.port.in.ChangeLoginPasswordResult;
import com.shinhan.corebank.auth.application.port.in.ChangeLoginPasswordUseCase;
import com.shinhan.corebank.auth.domain.exception.AuthErrorCode;
import com.shinhan.corebank.common.audit.AuditEventType;
import com.shinhan.corebank.common.audit.AuditLogService;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.customer.api.CustomerAuthenticationFacade;
import com.shinhan.corebank.customer.api.PasswordResetCustomerData;
import com.shinhan.corebank.customer.api.ResetCustomerPasswordCommand;
import com.shinhan.corebank.customer.api.ResetCustomerPasswordResult;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 로그인 비밀번호 변경의 검증·저장·감사 기록을 하나의 트랜잭션으로 처리한다.
@Service
@RequiredArgsConstructor
@Transactional
public class LoginPasswordChangeService implements ChangeLoginPasswordUseCase {
    private static final Pattern PASSWORD = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d\\s])\\S{8,15}$");
    private static final Pattern FOUR_REPEATED_CHARACTERS = Pattern.compile("(.)\\1{3}");

    private final CustomerAuthenticationFacade customerFacade;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final Clock clock;

    @Override
    public ChangeLoginPasswordResult change(ChangeLoginPasswordCommand command) {
        validateRequired(command);
        if (!command.newPassword().equals(command.newPasswordConfirm())) {
            throw new BusinessException(AuthErrorCode.PASSWORD_CONFIRMATION_MISMATCH);
        }

        PasswordResetCustomerData customer = customerFacade.findPasswordResetCustomerById(command.customerId());
        if (customer.accountLocked()) throw new BusinessException(AuthErrorCode.ACCOUNT_LOCKED);
        if (!passwordEncoder.matches(command.currentPassword(), customer.passwordHash())) {
            throw new BusinessException(AuthErrorCode.CURRENT_PASSWORD_MISMATCH);
        }
        validateNewPassword(command.newPassword(), command.userId());
        if (passwordEncoder.matches(command.newPassword(), customer.passwordHash())) {
            throw new BusinessException(AuthErrorCode.PREVIOUS_PASSWORD_REUSE);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        ResetCustomerPasswordResult result = customerFacade.resetPassword(new ResetCustomerPasswordCommand(
                customer.customerId(), customer.passwordHash(), passwordEncoder.encode(command.newPassword()), now));
        if (result == ResetCustomerPasswordResult.ACCOUNT_LOCKED) {
            throw new BusinessException(AuthErrorCode.ACCOUNT_LOCKED);
        }
        if (result == ResetCustomerPasswordResult.PASSWORD_CHANGED_CONCURRENTLY) {
            throw new BusinessException(CommonErrorCode.CONCURRENT_MODIFICATION);
        }

        auditLogService.record(
                customer.customerId(), null, AuditEventType.LOGIN_PASSWORD_CHANGE, command.requestIp(), true, Map.of());
        return new ChangeLoginPasswordResult(
                customer.customerId(), now.atZone(clock.getZone()).toOffsetDateTime());
    }

    private void validateRequired(ChangeLoginPasswordCommand command) {
        if (command == null
                || command.customerId() == null
                || isBlank(command.userId())
                || command.currentPassword() == null
                || command.newPassword() == null
                || command.newPasswordConfirm() == null
                || isBlank(command.requestIp())) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
    }

    // 길이·문자 조합과 아이디 포함·반복 문자·연속 숫자 금칙을 검증한다.
    private void validateNewPassword(String password, String userId) {
        if (!PASSWORD.matcher(password).matches()
                || password.toLowerCase(Locale.ROOT).contains(userId.toLowerCase(Locale.ROOT))
                || FOUR_REPEATED_CHARACTERS.matcher(password).find()
                || containsFourConsecutiveDigits(password)) {
            throw new BusinessException(AuthErrorCode.INVALID_PASSWORD_FORMAT);
        }
    }

    private boolean containsFourConsecutiveDigits(String password) {
        for (int start = 0; start <= password.length() - 4; start++) {
            String value = password.substring(start, start + 4);
            if (value.chars().allMatch(Character::isDigit)) {
                int gap = value.charAt(1) - value.charAt(0);
                if ((gap == 1 || gap == -1)
                        && value.charAt(2) - value.charAt(1) == gap
                        && value.charAt(3) - value.charAt(2) == gap) return true;
            }
        }
        return false;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
