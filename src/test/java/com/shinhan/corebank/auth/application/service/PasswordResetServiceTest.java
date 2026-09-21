package com.shinhan.corebank.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.shinhan.corebank.auth.application.port.in.IssuePasswordResetCommand;
import com.shinhan.corebank.auth.application.port.in.ResetPasswordCommand;
import com.shinhan.corebank.auth.application.port.out.PasswordResetRequestPort;
import com.shinhan.corebank.auth.domain.model.PasswordResetRequest;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.customer.api.CustomerAuthenticationFacade;
import com.shinhan.corebank.customer.api.PasswordResetCustomerData;
import com.shinhan.corebank.customer.api.ResetCustomerPasswordCommand;
import com.shinhan.corebank.customer.api.ResetCustomerPasswordResult;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@DisplayName("비밀번호 재설정 서비스 단위 테스트")
class PasswordResetServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-17T01:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final PasswordResetCustomerData CUSTOMER =
            new PasswordResetCustomerData(1L, "user01", "홍길동", "user@example.com", "old-password-hash", false);

    @Mock
    private CustomerAuthenticationFacade customerFacade;

    @Mock
    private PasswordResetRequestPort requestPort;

    @Mock
    private PasswordEncoder passwordEncoder;

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(customerFacade, requestPort, passwordEncoder, CLOCK);
    }

    @Test
    @DisplayName("일치하는 고객에게 180초 인증번호를 발급하고 기존 요청을 무효화한다")
    void issuePasswordResetCode() {
        given(customerFacade.findPasswordResetCustomerForUpdate("user01")).willReturn(Optional.of(CUSTOMER));
        given(passwordEncoder.encode(any())).willReturn("code-hash");
        given(requestPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        var result = service.issue(new IssuePasswordResetCommand("user01", "홍길동", "USER@example.com"));

        assertThat(result.passwordResetRequestId()).startsWith("PRR_");
        assertThat(result.verificationCode()).matches("\\d{6}");
        assertThat(result.expiresIn()).isEqualTo(180);
        verify(requestPort).invalidateActive(1L);

        ArgumentCaptor<PasswordResetRequest> requestCaptor = ArgumentCaptor.forClass(PasswordResetRequest.class);
        verify(requestPort).save(requestCaptor.capture());
        assertThat(requestCaptor.getValue().customerId()).isEqualTo(1L);
        assertThat(requestCaptor.getValue().target()).isEqualTo("user@example.com");
        assertThat(requestCaptor.getValue().used()).isFalse();
    }

    @Test
    @DisplayName("잠긴 고객은 인증번호를 발급받을 수 없다")
    void rejectLockedCustomer() {
        PasswordResetCustomerData locked =
                new PasswordResetCustomerData(1L, "user01", "홍길동", "user@example.com", "old-password-hash", true);
        given(customerFacade.findPasswordResetCustomerForUpdate("user01")).willReturn(Optional.of(locked));

        assertThatThrownBy(() -> service.issue(new IssuePasswordResetCommand("user01", "홍길동", "user@example.com")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(
                                exception.getErrorCode().getCode())
                        .isEqualTo("ATH0102"));

        verify(requestPort, never()).invalidateActive(any());
        verify(requestPort, never()).save(any());
    }

    @Test
    @DisplayName("인증번호가 틀리면 요청 상태와 고객 상태를 변경하지 않는다")
    void rejectMismatchedCodeWithoutIncreasingFailureCount() {
        given(requestPort.findByIdForUpdate("PRR_test")).willReturn(Optional.of(activeRequest()));
        given(passwordEncoder.matches("000000", "code-hash")).willReturn(false);

        assertThatThrownBy(() ->
                        service.reset(new ResetPasswordCommand("PRR_test", "000000", "NewPassword1!", "NewPassword1!")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(
                                exception.getErrorCode().getCode())
                        .isEqualTo("ATH0007"));

        verify(requestPort, never()).save(any());
        verify(customerFacade, never()).resetPassword(any());
    }

    @Test
    @DisplayName("재설정 성공 시 새 비밀번호를 저장하고 인증 요청을 사용 완료한다")
    void resetPassword() {
        PasswordResetRequest request = activeRequest();
        given(requestPort.findByIdForUpdate("PRR_test")).willReturn(Optional.of(request));
        given(passwordEncoder.matches("123456", "code-hash")).willReturn(true);
        given(customerFacade.findPasswordResetCustomerById(1L)).willReturn(CUSTOMER);
        given(passwordEncoder.matches("NewPassword1!", "old-password-hash")).willReturn(false);
        given(passwordEncoder.encode("NewPassword1!")).willReturn("new-password-hash");
        given(customerFacade.resetPassword(any())).willReturn(ResetCustomerPasswordResult.COMPLETED);
        given(requestPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        var result = service.reset(new ResetPasswordCommand("PRR_test", "123456", "NewPassword1!", "NewPassword1!"));

        assertThat(result.customerId()).isEqualTo(1L);
        assertThat(result.changedAt()).isEqualTo("2026-09-17T10:00+09:00");
        assertThat(request.used()).isTrue();
        assertThat(request.verifiedAt()).isEqualTo(LocalDateTime.of(2026, 9, 17, 10, 0));
        verify(customerFacade)
                .resetPassword(new ResetCustomerPasswordCommand(
                        1L, "old-password-hash", "new-password-hash", LocalDateTime.of(2026, 9, 17, 10, 0)));
        verify(requestPort).save(request);
    }

    @Test
    @DisplayName("아이디가 포함된 새 비밀번호는 사용할 수 없다")
    void rejectPasswordContainingUserId() {
        assertInvalidPassword("Aa!user01");
    }

    @Test
    @DisplayName("동일 문자가 4자리 연속된 새 비밀번호는 사용할 수 없다")
    void rejectPasswordWithFourRepeatedCharacters() {
        assertInvalidPassword("Aaaaa1!x");
    }

    @Test
    @DisplayName("오름차순 숫자가 4자리 연속된 새 비밀번호는 사용할 수 없다")
    void rejectPasswordWithAscendingDigits() {
        assertInvalidPassword("Ab!1234x");
    }

    @Test
    @DisplayName("내림차순 숫자가 4자리 연속된 새 비밀번호는 사용할 수 없다")
    void rejectPasswordWithDescendingDigits() {
        assertInvalidPassword("Ab!4321x");
    }

    @Test
    @DisplayName("이미 사용한 인증 요청은 다시 사용할 수 없다")
    void rejectUsedRequest() {
        PasswordResetRequest request = activeRequest();
        request.use(LocalDateTime.of(2026, 9, 17, 9, 59));
        given(requestPort.findByIdForUpdate("PRR_test")).willReturn(Optional.of(request));

        assertThatThrownBy(() ->
                        service.reset(new ResetPasswordCommand("PRR_test", "123456", "NewPassword1!", "NewPassword1!")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(
                                exception.getErrorCode().getCode())
                        .isEqualTo("ATH0202"));
    }

    @Test
    @DisplayName("인증 요청을 찾을 수 없으면 비밀번호를 재설정할 수 없다")
    void rejectMissingRequest() {
        given(requestPort.findByIdForUpdate("PRR_missing")).willReturn(Optional.empty());

        assertErrorCode(
                () -> service.reset(
                        new ResetPasswordCommand("PRR_missing", "123456", "NewPassword1!", "NewPassword1!")),
                "ATH0202");
    }

    @Test
    @DisplayName("만료된 인증번호로는 비밀번호를 재설정할 수 없다")
    void rejectExpiredCode() {
        PasswordResetRequest expired = PasswordResetRequest.issue(
                "PRR_test",
                1L,
                "user@example.com",
                "code-hash",
                LocalDateTime.of(2026, 9, 17, 10, 0),
                LocalDateTime.of(2026, 9, 17, 9, 57));
        given(requestPort.findByIdForUpdate("PRR_test")).willReturn(Optional.of(expired));

        assertErrorCode(
                () -> service.reset(new ResetPasswordCommand("PRR_test", "123456", "NewPassword1!", "NewPassword1!")),
                "ATH0008");
    }

    @Test
    @DisplayName("신규 비밀번호와 확인값이 다르면 재설정할 수 없다")
    void rejectPasswordConfirmationMismatch() {
        given(requestPort.findByIdForUpdate("PRR_test")).willReturn(Optional.of(activeRequest()));
        given(passwordEncoder.matches("123456", "code-hash")).willReturn(true);

        assertErrorCode(
                () -> service.reset(new ResetPasswordCommand("PRR_test", "123456", "NewPassword1!", "Different1!")),
                "ATH0002");
    }

    @Test
    @DisplayName("인증 후 고객 계정이 잠겼으면 재설정할 수 없다")
    void rejectLockedCustomerDuringReset() {
        PasswordResetCustomerData locked =
                new PasswordResetCustomerData(1L, "user01", "홍길동", "user@example.com", "old-password-hash", true);
        prepareVerifiedRequest(locked);

        assertErrorCode(
                () -> service.reset(new ResetPasswordCommand("PRR_test", "123456", "NewPassword1!", "NewPassword1!")),
                "ATH0102");
        verify(customerFacade, never()).resetPassword(any());
    }

    @Test
    @DisplayName("직전 비밀번호는 재사용할 수 없다")
    void rejectPreviousPasswordReuse() {
        prepareVerifiedRequest(CUSTOMER);
        given(passwordEncoder.matches("NewPassword1!", "old-password-hash")).willReturn(true);

        assertErrorCode(
                () -> service.reset(new ResetPasswordCommand("PRR_test", "123456", "NewPassword1!", "NewPassword1!")),
                "ATH0003");
        verify(customerFacade, never()).resetPassword(any());
    }

    @Test
    @DisplayName("저장 직전 고객 계정이 잠겼으면 재설정을 취소한다")
    void rejectAccountLockedWhileSavingPassword() {
        preparePasswordChange(ResetCustomerPasswordResult.ACCOUNT_LOCKED);

        assertErrorCode(
                () -> service.reset(new ResetPasswordCommand("PRR_test", "123456", "NewPassword1!", "NewPassword1!")),
                "ATH0102");
        verify(requestPort, never()).save(any());
    }

    @Test
    @DisplayName("다른 요청이 비밀번호를 먼저 변경하면 재설정을 취소한다")
    void rejectConcurrentPasswordChange() {
        preparePasswordChange(ResetCustomerPasswordResult.PASSWORD_CHANGED_CONCURRENTLY);

        assertErrorCode(
                () -> service.reset(new ResetPasswordCommand("PRR_test", "123456", "NewPassword1!", "NewPassword1!")),
                "CMN0303");
        verify(requestPort, never()).save(any());
    }

    @Test
    @DisplayName("기본 비밀번호 형식을 위반하면 재설정할 수 없다")
    void rejectInvalidPasswordFormat() {
        given(requestPort.findByIdForUpdate("PRR_test")).willReturn(Optional.of(activeRequest()));
        given(passwordEncoder.matches("123456", "code-hash")).willReturn(true);

        assertErrorCode(
                () -> service.reset(new ResetPasswordCommand("PRR_test", "123456", "OnlyLetters", "OnlyLetters")),
                "ATH0001");
        verify(customerFacade, never()).findPasswordResetCustomerById(any());
    }

    @Test
    @DisplayName("인증번호 발급 명령이 없으면 필수값 오류를 반환한다")
    void rejectMissingIssueCommand() {
        assertErrorCode(() -> service.issue(null), "CMN0002");
        verify(customerFacade, never()).findPasswordResetCustomerForUpdate(any());
    }

    @Test
    @DisplayName("인증번호 발급 필수값 누락을 각 필드별로 거부한다")
    void rejectEveryMissingIssueField() {
        assertErrorCode(() -> service.issue(new IssuePasswordResetCommand(null, "홍길동", "user@example.com")), "CMN0002");
        assertErrorCode(
                () -> service.issue(new IssuePasswordResetCommand("user01", null, "user@example.com")), "CMN0002");
        assertErrorCode(() -> service.issue(new IssuePasswordResetCommand("user01", "홍길동", null)), "CMN0002");
    }

    @Test
    @DisplayName("인증번호 발급 입력값의 각 길이와 형식 경계를 검증한다")
    void rejectEveryInvalidIssueField() {
        assertErrorCode(() -> service.issue(new IssuePasswordResetCommand(" ", "홍길동", "user@example.com")), "CMN0001");
        assertErrorCode(
                () -> service.issue(new IssuePasswordResetCommand("usr1", "홍길동", "user@example.com")), "CMN0001");
        assertErrorCode(
                () -> service.issue(new IssuePasswordResetCommand("u".repeat(21), "홍길동", "user@example.com")),
                "CMN0001");
        assertErrorCode(
                () -> service.issue(new IssuePasswordResetCommand("user01", " ", "user@example.com")), "CMN0001");
        assertErrorCode(
                () -> service.issue(new IssuePasswordResetCommand("user01", "가".repeat(51), "user@example.com")),
                "CMN0001");
        assertErrorCode(
                () -> service.issue(new IssuePasswordResetCommand("user01", "홍길동", "a".repeat(101))), "CMN0001");
    }

    @Test
    @DisplayName("비밀번호 재설정 명령이 없으면 필수값 오류를 반환한다")
    void rejectMissingResetCommand() {
        assertErrorCode(() -> service.reset(null), "CMN0002");
        verify(requestPort, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("비밀번호 재설정 필수값 누락을 각 필드별로 거부한다")
    void rejectEveryMissingResetField() {
        assertErrorCode(
                () -> service.reset(new ResetPasswordCommand(null, "123456", "NewPassword1!", "NewPassword1!")),
                "CMN0002");
        assertErrorCode(
                () -> service.reset(new ResetPasswordCommand("PRR_test", null, "NewPassword1!", "NewPassword1!")),
                "CMN0002");
        assertErrorCode(
                () -> service.reset(new ResetPasswordCommand("PRR_test", "123456", null, "NewPassword1!")), "CMN0002");
        assertErrorCode(
                () -> service.reset(new ResetPasswordCommand("PRR_test", "123456", "NewPassword1!", null)), "CMN0002");
    }

    @Test
    @DisplayName("숫자 6자리 형식이 아닌 인증번호를 거부한다")
    void rejectMalformedVerificationCode() {
        given(requestPort.findByIdForUpdate("PRR_test")).willReturn(Optional.of(activeRequest()));

        assertErrorCode(
                () -> service.reset(new ResetPasswordCommand("PRR_test", "12345", "NewPassword1!", "NewPassword1!")),
                "ATH0007");
    }

    @Test
    @DisplayName("연속 숫자가 아닌 네 자리 조합은 정상 비밀번호로 허용한다")
    void allowNonConsecutiveFourDigitCombinations() {
        for (String password : new String[] {"Ax!1357z", "Ax!1245z", "Ax!1235z", "Ax!9865z", "Ax!9875z"}) {
            PasswordResetRequest request = activeRequest();
            given(requestPort.findByIdForUpdate("PRR_test")).willReturn(Optional.of(request));
            given(passwordEncoder.matches("123456", "code-hash")).willReturn(true);
            given(customerFacade.findPasswordResetCustomerById(1L)).willReturn(CUSTOMER);
            given(passwordEncoder.matches(password, "old-password-hash")).willReturn(false);
            given(passwordEncoder.encode(password)).willReturn("new-password-hash");
            given(customerFacade.resetPassword(any())).willReturn(ResetCustomerPasswordResult.COMPLETED);

            assertThat(service.reset(new ResetPasswordCommand("PRR_test", "123456", password, password))
                            .customerId())
                    .isEqualTo(1L);
        }
    }

    @Test
    @DisplayName("인증번호 발급 대상 고객을 찾을 수 없으면 동일한 사용자 없음 오류를 반환한다")
    void rejectMissingCustomerDuringIssue() {
        given(customerFacade.findPasswordResetCustomerForUpdate("user01")).willReturn(Optional.empty());

        assertErrorCode(
                () -> service.issue(new IssuePasswordResetCommand("user01", "홍길동", "user@example.com")), "ATH0201");
        verify(requestPort, never()).save(any());
    }

    @Test
    @DisplayName("이메일 형식이 올바르지 않으면 인증번호를 발급할 수 없다")
    void rejectInvalidEmailDuringIssue() {
        assertErrorCode(
                () -> service.issue(new IssuePasswordResetCommand("user01", "홍길동", "invalid-email")), "CMN0001");
        verify(customerFacade, never()).findPasswordResetCustomerForUpdate(any());
    }

    @Test
    @DisplayName("고객 ID를 확인할 재설정 요청이 없으면 오류를 반환한다")
    void rejectMissingRequestWhenResolvingCustomerId() {
        given(requestPort.findById("PRR_missing")).willReturn(Optional.empty());

        assertErrorCode(() -> service.resolveCustomerId("PRR_missing"), "ATH0202");
    }

    @Test
    @DisplayName("재설정 요청 ID로 고객 ID를 확인한다")
    void resolveCustomerId() {
        given(requestPort.findById("PRR_test")).willReturn(Optional.of(activeRequest()));

        assertThat(service.resolveCustomerId("PRR_test")).isEqualTo(1L);
    }

    private void prepareVerifiedRequest(PasswordResetCustomerData customer) {
        given(requestPort.findByIdForUpdate("PRR_test")).willReturn(Optional.of(activeRequest()));
        given(passwordEncoder.matches("123456", "code-hash")).willReturn(true);
        given(customerFacade.findPasswordResetCustomerById(1L)).willReturn(customer);
    }

    private void preparePasswordChange(ResetCustomerPasswordResult result) {
        prepareVerifiedRequest(CUSTOMER);
        given(passwordEncoder.matches("NewPassword1!", "old-password-hash")).willReturn(false);
        given(passwordEncoder.encode("NewPassword1!")).willReturn("new-password-hash");
        given(customerFacade.resetPassword(any())).willReturn(result);
    }

    private void assertErrorCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, String code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(
                        exception.getErrorCode().getCode())
                .isEqualTo(code));
    }

    private void assertInvalidPassword(String newPassword) {
        given(requestPort.findByIdForUpdate("PRR_test")).willReturn(Optional.of(activeRequest()));
        given(passwordEncoder.matches("123456", "code-hash")).willReturn(true);
        given(customerFacade.findPasswordResetCustomerById(1L)).willReturn(CUSTOMER);

        assertThatThrownBy(
                        () -> service.reset(new ResetPasswordCommand("PRR_test", "123456", newPassword, newPassword)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(
                                exception.getErrorCode().getCode())
                        .isEqualTo("ATH0001"));

        verify(customerFacade, never()).resetPassword(any());
        verify(requestPort, never()).save(any());
    }

    private PasswordResetRequest activeRequest() {
        return PasswordResetRequest.issue(
                "PRR_test",
                1L,
                "user@example.com",
                "code-hash",
                LocalDateTime.of(2026, 9, 17, 10, 3),
                LocalDateTime.of(2026, 9, 17, 10, 0));
    }
}
