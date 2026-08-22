package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.application.port.in.ChangeAccountPasswordCommand;
import com.shinhan.corebank.account.application.port.in.ChangeAccountPasswordResult;
import com.shinhan.corebank.account.application.port.in.ChangeAccountPasswordUseCase;
import com.shinhan.corebank.account.application.port.out.AccountPasswordChangeAuthVerificationPort;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.exception.AccountErrorCode;
import com.shinhan.corebank.account.domain.exception.AccountPasswordErrorCode;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.common.audit.AuditEventType;
import com.shinhan.corebank.common.audit.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.Objects;
import java.util.Map;
import java.util.regex.Pattern;

// 소유 계좌의 인증 토큰을 소비하고 신규 BCrypt 비밀번호를 저장한다.
@Service
@RequiredArgsConstructor
@Transactional
public class AccountPasswordChangeService
        implements ChangeAccountPasswordUseCase {

    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^\\d{4}$");
    private static final ZoneId KOREA_ZONE =
            ZoneId.of("Asia/Seoul");

    private final AccountPersistencePort accountPersistencePort;
    private final AccountPasswordChangeAuthVerificationPort
            authVerificationPort;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    @Override
    public ChangeAccountPasswordResult change(
            ChangeAccountPasswordCommand command
    ) {
        Objects.requireNonNull(command, "command must not be null");
        validateCommand(command);

        Account account = accountPersistencePort
                .findByAccountIdAndCustomerIdForUpdate(
                        command.accountId(),
                        command.customerId()
                )
                .orElseThrow(() -> new BusinessException(
                        AccountErrorCode.ACCOUNT_NOT_FOUND_OR_FORBIDDEN
                ));

        validateAccountState(account);

        authVerificationPort.verifyAccountPasswordToken(
                command.accountPasswordAuthToken(),
                command.customerId(),
                command.accountId()
        );
        authVerificationPort.verifyOtpToken(
                command.otpAuthToken(),
                command.customerId(),
                command.accountId()
        );

        account.changePassword(
                passwordEncoder.encode(command.newAccountPassword())
        );

        Account saved =
                accountPersistencePort.updatePasswordState(account);

        // 인증수단 변경 성공 사실만 기록하고 비밀번호와 토큰은 감사 상세에서 제외한다.
        auditLogService.record(
                command.customerId(),
                null,
                AuditEventType.ACCOUNT_PASSWORD_CHANGE,
                command.requestIp(),
                true,
                Map.of("accountId", command.accountId())
        );

        return new ChangeAccountPasswordResult(
                saved.getAccountId(),
                saved.getUpdatedAt()
                        .atZone(KOREA_ZONE)
                        .toOffsetDateTime()
        );
    }

    // 필수값과 신규 비밀번호 숫자 4자리 및 확인값 일치를 검증한다.
    private void validateCommand(
            ChangeAccountPasswordCommand command
    ) {
        if (command.customerId() == null
                || command.accountId() == null
                || isBlank(command.otpAuthToken())
                || isBlank(command.accountPasswordAuthToken())
                || command.newAccountPassword() == null
                || command.newAccountPasswordConfirm() == null
                || isBlank(command.requestIp())) {
            throw new BusinessException(
                    CommonErrorCode.REQUIRED_FIELD_MISSING
            );
        }

        if (command.customerId() <= 0
                || command.accountId() <= 0
                || !PASSWORD_PATTERN.matcher(
                        command.newAccountPassword()
                ).matches()
                || !PASSWORD_PATTERN.matcher(
                        command.newAccountPasswordConfirm()
                ).matches()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        if (!command.newAccountPassword().equals(
                command.newAccountPasswordConfirm()
        )) {
            throw new BusinessException(
                    AccountPasswordErrorCode
                            .NEW_PASSWORD_CONFIRM_MISMATCH
            );
        }
    }

    // 잠금 또는 거래정지·해지 계좌는 인증 토큰을 소비하기 전에 차단한다.
    private void validateAccountState(Account account) {
        if (account.isPasswordLocked()) {
            throw new BusinessException(
                    AccountPasswordErrorCode.PASSWORD_LOCKED
            );
        }

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException(
                    AccountErrorCode.INVALID_ACCOUNT_STATUS
            );
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
