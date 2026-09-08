package com.shinhan.corebank.auth.application.service;

import com.shinhan.corebank.auth.application.port.in.FindIdCommand;
import com.shinhan.corebank.auth.application.port.in.FindIdResult;
import com.shinhan.corebank.auth.application.port.out.FindIdAccountVerificationPort;
import com.shinhan.corebank.auth.application.port.out.FindIdAccountVerificationResult;
import com.shinhan.corebank.auth.application.port.out.FindIdAccountVerificationStatus;
import com.shinhan.corebank.auth.application.port.out.FindIdCustomerCandidate;
import com.shinhan.corebank.auth.application.port.out.FindIdCustomerPort;
import com.shinhan.corebank.auth.domain.exception.AuthErrorCode;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 아이디 찾기의 입력 검증과 고객·계좌 본인확인 분기를 검증한다.
class FindIdServiceTest {

    private final FindIdCustomerPort customerPort =
            mock(FindIdCustomerPort.class);
    private final FindIdAccountVerificationPort accountPort =
            mock(FindIdAccountVerificationPort.class);
    private final FindIdService service =
            new FindIdService(customerPort, accountPort);

    @Test
    @DisplayName("동명이인 중 계좌 소유자로 확인된 고객의 전체 아이디를 반환한다")
    void returnsVerifiedCustomersFullUserId() {
        FindIdCommand command = validCommand();
        List<FindIdCustomerCandidate> candidates = List.of(
                new FindIdCustomerCandidate(1L, "first-user"),
                new FindIdCustomerCandidate(2L, "DUDGNS389")
        );
        given(customerPort.findAllByIdentity(
                "홍길동",
                LocalDate.of(1999, 1, 1)
        )).willReturn(candidates);
        given(accountPort.verify(
                Set.of(1L, 2L),
                "110550051877",
                "1234"
        )).willReturn(new FindIdAccountVerificationResult(
                FindIdAccountVerificationStatus.VERIFIED,
                2L
        ));

        FindIdResult result = service.findId(command);

        assertThat(result.userId()).isEqualTo("DUDGNS389");
    }

    @Test
    @DisplayName("네 필수 입력값 중 하나라도 누락되면 CMN0002를 반환한다")
    void rejectsMissingRequiredField() {
        assertThatThrownBy(() -> service.findId(null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING)
                );
        List<FindIdCommand> missingCommands = List.of(
                new FindIdCommand(null, "1999-01-01", "110550051877", "1234"),
                new FindIdCommand("홍길동", "", "110550051877", "1234"),
                new FindIdCommand("홍길동", "1999-01-01", " ", "1234"),
                new FindIdCommand("홍길동", "1999-01-01", "110550051877", null)
        );
        for (FindIdCommand command : missingCommands) {
            assertThatThrownBy(() -> service.findId(command))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING)
                    );
        }
        verify(customerPort, never()).findAllByIdentity(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    @DisplayName("생년월일·계좌번호·계좌비밀번호 형식이 잘못되면 CMN0001을 반환한다")
    void rejectsInvalidFormat() {
        FindIdCommand command = new FindIdCommand(
                "홍길동",
                "19990101",
                "110-550-051877",
                "12a4"
        );

        assertThatThrownBy(() -> service.findId(command))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(CommonErrorCode.INVALID_INPUT)
                );
    }

    @Test
    @DisplayName("성명과 생년월일이 일치하는 고객이 없으면 ATH0201을 반환한다")
    void rejectsUnknownCustomer() {
        given(customerPort.findAllByIdentity(
                "홍길동",
                LocalDate.of(1999, 1, 1)
        )).willReturn(List.of());

        assertThatThrownBy(() -> service.findId(validCommand()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(AuthErrorCode.USER_NOT_FOUND)
                );
        verify(accountPort, never()).verify(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = FindIdAccountVerificationStatus.class,
            names = {"INFORMATION_MISMATCH", "PASSWORD_MISMATCH"}
    )
    @DisplayName("계좌정보 또는 비밀번호 불일치는 항목을 구분하지 않고 ATH0009를 반환한다")
    void hidesMismatchedField(FindIdAccountVerificationStatus status) {
        givenCandidate();
        given(accountPort.verify(
                Set.of(1L),
                "110550051877",
                "1234"
        )).willReturn(new FindIdAccountVerificationResult(status, 1L));

        assertThatThrownBy(() -> service.findId(validCommand()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(AuthErrorCode.IDENTITY_INFORMATION_MISMATCH)
                );
    }

    @Test
    @DisplayName("계좌비밀번호가 5회 실패해 잠기면 ATH0102를 반환한다")
    void rejectsLockedAccountPassword() {
        givenCandidate();
        given(accountPort.verify(
                Set.of(1L),
                "110550051877",
                "1234"
        )).willReturn(new FindIdAccountVerificationResult(
                FindIdAccountVerificationStatus.LOCKED,
                1L
        ));

        assertThatThrownBy(() -> service.findId(validCommand()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(AuthErrorCode.ACCOUNT_LOCKED)
                );
    }

    private void givenCandidate() {
        given(customerPort.findAllByIdentity(
                "홍길동",
                LocalDate.of(1999, 1, 1)
        )).willReturn(List.of(
                new FindIdCustomerCandidate(1L, "DUDGNS389")
        ));
    }

    private FindIdCommand validCommand() {
        return new FindIdCommand(
                "홍길동",
                "1999-01-01",
                "110550051877",
                "1234"
        );
    }
}
