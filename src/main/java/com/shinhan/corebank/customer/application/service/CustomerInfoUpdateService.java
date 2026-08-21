package com.shinhan.corebank.customer.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.customer.application.port.in.UpdateCustomerInfoCommand;
import com.shinhan.corebank.customer.application.port.in.UpdateCustomerInfoResult;
import com.shinhan.corebank.customer.application.port.in.UpdateCustomerInfoUseCase;
import com.shinhan.corebank.customer.application.port.out.CustomerPersistencePort;
import com.shinhan.corebank.customer.application.port.out.EmailChangeVerificationPort;
import com.shinhan.corebank.customer.domain.exception.CustomerErrorCode;
import com.shinhan.corebank.customer.domain.model.Customer;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

// 로그인 고객의 연락처 변경 검증·인증·저장을 하나의 유스케이스로 처리한다.
@Service
@RequiredArgsConstructor
public class CustomerInfoUpdateService implements UpdateCustomerInfoUseCase {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\d{11}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+"
                    + "@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+$"
    );

    private final CustomerPersistencePort customerPersistencePort;
    private final EmailChangeVerificationPort emailChangeVerificationPort;
    private final CustomerInfoMasker customerInfoMasker;
    private final Clock clock;

    // 요청 항목을 검증하고 이메일 인증 토큰 소비와 고객정보 저장을 처리한다.
    @Override
    @Transactional
    public UpdateCustomerInfoResult update(UpdateCustomerInfoCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(
                command.customerId(),
                "customerId must not be null"
        );
        validateRequestedFields(command);

        Customer customer = customerPersistencePort.findByIdForUpdate(
                command.customerId()
        ).orElseThrow(() -> new IllegalStateException(
                "로그인 고객의 기본정보를 찾을 수 없습니다."
        ));

        String requestedPhoneNumber = resolvePhoneNumber(command, customer);
        String requestedEmail = resolveEmail(command, customer);
        boolean phoneChanged = !requestedPhoneNumber.equals(
                customer.getPhoneNumber()
        );
        boolean emailChanged = !requestedEmail.equalsIgnoreCase(
                customer.getEmail()
        );

        if (!phoneChanged && !emailChanged) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        if (emailChanged) {
            validateEmailVerificationToken(command.emailVerificationToken());
            if (customerPersistencePort.existsByEmail(requestedEmail)) {
                throw new BusinessException(CustomerErrorCode.DUPLICATE_EMAIL);
            }
        }

        customer.changeContactInfo(
                requestedPhoneNumber,
                requestedEmail,
                LocalDateTime.now(clock)
        );

        Customer savedCustomer = updateContactInfo(customer, emailChanged);

        // DB 제약 확인 후 토큰을 소비하고 실패하면 고객정보 트랜잭션을 롤백한다.
        if (emailChanged) {
            emailChangeVerificationPort.verifyAndConsume(
                    command.emailVerificationToken(),
                    requestedEmail
            );
        }

        return toResult(savedCustomer);
    }

    // 휴대폰·이메일 중 하나 이상이 전달되고 각 입력 형식이 올바른지 확인한다.
    private void validateRequestedFields(UpdateCustomerInfoCommand command) {
        if (command.phoneNumber() == null && command.email() == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        if (command.phoneNumber() != null
                && !PHONE_PATTERN.matcher(command.phoneNumber()).matches()) {
            throw new BusinessException(CustomerErrorCode.INVALID_PHONE_NUMBER);
        }
        if (command.email() != null
                && (command.email().length() > 100
                || !command.email().equals(command.email().trim())
                || !EMAIL_PATTERN.matcher(command.email()).matches())) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        if (command.email() == null
                && command.emailVerificationToken() != null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }

    // 미전달 휴대폰 번호는 현재 저장값으로 유지한다.
    private String resolvePhoneNumber(
            UpdateCustomerInfoCommand command,
            Customer customer
    ) {
        return command.phoneNumber() == null
                ? customer.getPhoneNumber()
                : command.phoneNumber();
    }

    // 전달된 이메일은 비교와 중복 확인을 위해 소문자로 정규화한다.
    private String resolveEmail(
            UpdateCustomerInfoCommand command,
            Customer customer
    ) {
        return command.email() == null
                ? customer.getEmail()
                : command.email().toLowerCase(Locale.ROOT);
    }

    // 실제 이메일 변경에는 인증 완료 토큰이 반드시 있어야 한다.
    private void validateEmailVerificationToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
    }

    // 이메일 유니크 제약 경합도 외부에는 ATH0302로 일관되게 반환한다.
    private Customer updateContactInfo(
            Customer customer,
            boolean emailChanged
    ) {
        try {
            return customerPersistencePort.updateContactInfo(customer);
        } catch (DataIntegrityViolationException exception) {
            if (emailChanged) {
                throw new BusinessException(
                        CustomerErrorCode.DUPLICATE_EMAIL,
                        exception
                );
            }
            throw exception;
        }
    }

    // 저장 결과를 마스킹하고 KST 오프셋을 포함한 응답으로 변환한다.
    private UpdateCustomerInfoResult toResult(Customer customer) {
        return new UpdateCustomerInfoResult(
                customer.getCustomerId(),
                customerInfoMasker.maskPhoneNumber(
                        customer.getPhoneNumber()
                ),
                customerInfoMasker.maskEmail(customer.getEmail()),
                customer.getUpdatedAt()
                        .atZone(clock.getZone())
                        .toOffsetDateTime()
        );
    }
}
