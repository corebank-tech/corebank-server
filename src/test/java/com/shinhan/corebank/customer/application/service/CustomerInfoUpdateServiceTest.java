package com.shinhan.corebank.customer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.customer.application.port.in.UpdateCustomerInfoCommand;
import com.shinhan.corebank.customer.application.port.in.UpdateCustomerInfoResult;
import com.shinhan.corebank.customer.application.port.out.CustomerPersistencePort;
import com.shinhan.corebank.customer.application.port.out.EmailChangeVerificationPort;
import com.shinhan.corebank.customer.domain.exception.CustomerErrorCode;
import com.shinhan.corebank.customer.domain.model.Customer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerInfoUpdateServiceTest {

    private static final Long CUSTOMER_ID = 1L;
    private static final String EMAIL_TOKEN = "EMAIL_VERIFICATION_token";

    @Mock
    CustomerPersistencePort customerPersistencePort;

    @Mock
    EmailChangeVerificationPort emailChangeVerificationPort;

    private CustomerInfoUpdateService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-21T07:20:00Z"),
                ZoneId.of("Asia/Seoul")
        );
        service = new CustomerInfoUpdateService(
                customerPersistencePort,
                emailChangeVerificationPort,
                new CustomerInfoMasker(),
                clock
        );
    }

    @Test
    @DisplayName("휴대폰 번호와 인증된 이메일을 함께 변경한다")
    void updatesPhoneNumberAndVerifiedEmail() {
        givenCustomer();
        given(customerPersistencePort.existsByEmail("newmail@corebank.com"))
                .willReturn(false);
        givenPersistedCustomer();

        UpdateCustomerInfoResult result = service.update(command(
                "01087654321",
                "NewMail@CoreBank.com",
                EMAIL_TOKEN
        ));

        assertThat(result.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(result.phoneNumber()).isEqualTo("010****4321");
        assertThat(result.email()).isEqualTo("newm***@corebank.com");
        assertThat(result.updatedAt().toString())
                .isEqualTo("2026-08-21T16:20+09:00");
        verify(emailChangeVerificationPort).verifyAndConsume(
                EMAIL_TOKEN,
                "newmail@corebank.com"
        );
    }

    @Test
    @DisplayName("휴대폰 번호만 변경하면 이메일 인증 토큰을 사용하지 않는다")
    void updatesOnlyPhoneNumberWithoutEmailVerification() {
        givenCustomer();
        givenPersistedCustomer();

        UpdateCustomerInfoResult result = service.update(command(
                "01087654321",
                null,
                null
        ));

        assertThat(result.phoneNumber()).isEqualTo("010****4321");
        assertThat(result.email()).isEqualTo("curr***@corebank.com");
        verify(emailChangeVerificationPort, never())
                .verifyAndConsume(any(), any());
    }

    @Test
    @DisplayName("변경 항목이 없거나 저장값과 같으면 CMN0001이다")
    void rejectsMissingOrUnchangedFields() {
        assertError(
                command(null, null, null),
                CommonErrorCode.INVALID_INPUT
        );

        givenCustomer();
        assertError(
                command("01012345678", "current@corebank.com", null),
                CommonErrorCode.INVALID_INPUT
        );
    }

    @Test
    @DisplayName("휴대폰 번호가 숫자 11자리가 아니면 MYP0001이다")
    void rejectsInvalidPhoneNumber() {
        assertError(
                command("010-8765-4321", null, null),
                CustomerErrorCode.INVALID_PHONE_NUMBER
        );
        verify(customerPersistencePort, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("이메일 형식이 잘못되면 CMN0001이다")
    void rejectsInvalidEmail() {
        assertError(
                command(null, "invalid-email", EMAIL_TOKEN),
                CommonErrorCode.INVALID_INPUT
        );
        verify(customerPersistencePort, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("이메일 변경 인증 토큰이 없으면 CMN0002이다")
    void requiresVerificationTokenForEmailChange() {
        givenCustomer();

        assertError(
                command(null, "newmail@corebank.com", null),
                CommonErrorCode.REQUIRED_FIELD_MISSING
        );
        verify(customerPersistencePort, never()).updateContactInfo(any());
    }

    @Test
    @DisplayName("이미 가입된 이메일로 변경하면 ATH0302이다")
    void rejectsDuplicateEmail() {
        givenCustomer();
        given(customerPersistencePort.existsByEmail("used@corebank.com"))
                .willReturn(true);

        assertError(
                command(null, "used@corebank.com", EMAIL_TOKEN),
                CustomerErrorCode.DUPLICATE_EMAIL
        );
        verify(emailChangeVerificationPort, never())
                .verifyAndConsume(any(), any());
        verify(customerPersistencePort, never()).updateContactInfo(any());
    }

    // 공통 테스트 고객 조회와 저장 응답을 준비한다.
    private void givenCustomer() {
        given(customerPersistencePort.findByIdForUpdate(CUSTOMER_ID))
                .willReturn(Optional.of(customer()));
    }

    // Auditing이 반영된 영속화 결과를 반환해 입력 객체 echo로 인한 착시를 방지한다.
    private void givenPersistedCustomer() {
        given(customerPersistencePort.updateContactInfo(any()))
                .willAnswer(invocation -> persistedCustomer(
                        invocation.getArgument(0)
                ));
    }

    // 테스트 입력값으로 고객정보 변경 command를 생성한다.
    private UpdateCustomerInfoCommand command(
            String phoneNumber,
            String email,
            String token
    ) {
        return new UpdateCustomerInfoCommand(
                CUSTOMER_ID,
                phoneNumber,
                email,
                token
        );
    }

    // 기대 오류코드와 실제 BusinessException을 비교한다.
    private void assertError(
            UpdateCustomerInfoCommand command,
            Object expectedErrorCode
    ) {
        BusinessException exception = catchThrowableOfType(
                () -> service.update(command),
                BusinessException.class
        );
        assertThat(exception.getErrorCode()).isEqualTo(expectedErrorCode);
    }

    // 고객정보 변경 테스트에 사용할 현재 고객을 복원한다.
    private Customer customer() {
        LocalDateTime joinedAt = LocalDateTime.of(2025, 3, 10, 9, 0);
        return Customer.restore(
                CUSTOMER_ID,
                "honggildong",
                null,
                "password-hash",
                "홍길동",
                LocalDate.of(1995, 3, 10),
                "current@corebank.com",
                "01012345678",
                0,
                false,
                null,
                null,
                null,
                null,
                joinedAt,
                joinedAt,
                joinedAt
        );
    }

    // 저장 시점의 updatedAt을 포함한 고객 객체로 실제 persistence 반환값을 모사한다.
    private Customer persistedCustomer(Customer customer) {
        return Customer.restore(
                customer.getCustomerId(),
                customer.getUserId(),
                customer.getExistingBankCustomerId(),
                customer.getPasswordHash(),
                customer.getUserName(),
                customer.getBirthDate(),
                customer.getEmail(),
                customer.getPhoneNumber(),
                customer.getLoginFailureCount(),
                customer.isAccountLocked(),
                customer.getLastLoginAt(),
                customer.getLastLoginIp(),
                customer.getPreviousLoginAt(),
                customer.getPasswordChangedAt(),
                customer.getJoinedAt(),
                customer.getCreatedAt(),
                LocalDateTime.of(2026, 8, 21, 16, 20)
        );
    }
}
