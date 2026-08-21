package com.shinhan.corebank.signup.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.signup.application.port.in.SignupConfirmationResult;
import com.shinhan.corebank.signup.application.port.out.ExistingBankCustomerProfilePort;
import com.shinhan.corebank.signup.application.port.out.TempSignupTokenPort;
import com.shinhan.corebank.signup.domain.model.ExistingBankCustomerProfile;
import com.shinhan.corebank.signup.domain.model.TempSignupTokenPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 회원가입 확인정보의 마스킹과 tempSignupToken 비소비 조회를 검증한다.
@ExtendWith(MockitoExtension.class)
class SignupConfirmationServiceTest {

    @Mock TempSignupTokenPort tempTokenPort;
    @Mock ExistingBankCustomerProfilePort profilePort;

    @Test
    void masksConfirmationWithoutConsumingToken() {
        TempSignupTokenPayload payload = new TempSignupTokenPayload(
                List.of(), "BANK_CUSTOMER_001", "BANK_ACCOUNT_001",
                "honggildong", "hash", "hong@corebank.example.com",
                "01012345678", Instant.parse("2026-08-20T01:00:00Z")
        );
        given(tempTokenPort.find("TEMP")).willReturn(Optional.of(payload));
        given(profilePort.findByCustomerId("BANK_CUSTOMER_001"))
                .willReturn(Optional.of(new ExistingBankCustomerProfile(
                        "BANK_CUSTOMER_001", "홍길동", LocalDate.of(1990, 1, 1)
                )));
        SignupConfirmationService service = new SignupConfirmationService(
                tempTokenPort, profilePort
        );

        SignupConfirmationResult result = service.getConfirmation("TEMP");

        assertThat(result.userName()).isEqualTo("홍*동");
        assertThat(result.userId()).isEqualTo("honggildong");
        assertThat(result.birthDate()).isEqualTo("90.01.01");
        assertThat(result.phoneNumber()).isEqualTo("010-****-5678");
        assertThat(result.email()).isEqualTo("hon*@corebank.example.com");
        verify(tempTokenPort, never()).consume("TEMP");
    }

    @Test
    void invalidTempTokenReturnsCmn0001() {
        given(tempTokenPort.find("INVALID")).willReturn(Optional.empty());
        SignupConfirmationService service = new SignupConfirmationService(
                tempTokenPort, profilePort
        );

        BusinessException exception = catchThrowableOfType(
                () -> service.getConfirmation("INVALID"),
                BusinessException.class
        );

        assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "a@example.com,     '*@example.com'",
            "ab@example.com,    'a*@example.com'",
            "abc@example.com,   'ab*@example.com'",
            "abcd@example.com,  'abc*@example.com'",
            "abcde@example.com, 'abc**@example.com'"
    })
    void masksAtLeastOneEmailCharacterAndExposesAtMostThree(
            String email,
            String expected
    ) {
        TempSignupTokenPayload payload = new TempSignupTokenPayload(
                List.of(), "BANK_CUSTOMER_001", "BANK_ACCOUNT_001",
                "honggildong", "hash", email,
                "01012345678", Instant.parse("2026-08-20T01:00:00Z")
        );
        given(tempTokenPort.find("TEMP")).willReturn(Optional.of(payload));
        given(profilePort.findByCustomerId("BANK_CUSTOMER_001"))
                .willReturn(Optional.of(new ExistingBankCustomerProfile(
                        "BANK_CUSTOMER_001", "홍길동", LocalDate.of(1990, 1, 1)
                )));
        SignupConfirmationService service = new SignupConfirmationService(
                tempTokenPort, profilePort
        );

        SignupConfirmationResult result = service.getConfirmation("TEMP");

        assertThat(result.email()).isEqualTo(expected);
    }
}
