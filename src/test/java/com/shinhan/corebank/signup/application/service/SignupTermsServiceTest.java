package com.shinhan.corebank.signup.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.signup.application.port.in.CheckTermsAgreementCommand;
import com.shinhan.corebank.signup.application.port.in.TermsAgreementResult;
import com.shinhan.corebank.signup.application.port.out.AuthTokenGeneratorPort;
import com.shinhan.corebank.signup.application.port.out.SignupTermsQueryPort;
import com.shinhan.corebank.signup.application.port.out.TermsAuthTokenPort;
import com.shinhan.corebank.signup.config.SignupTokenProperties;
import com.shinhan.corebank.signup.domain.exception.SignupErrorCode;
import com.shinhan.corebank.signup.domain.model.SignupTerm;
import com.shinhan.corebank.signup.domain.model.TermsAuthTokenPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SignupTermsServiceTest {

    private static final Duration TERMS_AUTH_TTL = Duration.ofMinutes(30);
    private static final String TERMS_AUTH_TOKEN = "TERMS_AUTH_test-token";

    private static final SignupTerm REQUIRED_SERVICE = new SignupTerm(
            "1",
            "TERMS_SERVICE",
            "v1.0",
            "서비스 이용약관",
            "서비스 이용약관 내용",
            true,
            false
    );

    private static final SignupTerm REQUIRED_PRIVACY = new SignupTerm(
            "2",
            "TERMS_PRIVACY",
            "v1.0",
            "개인정보 수집·이용 동의",
            "개인정보 약관 내용",
            true,
            false
    );

    private static final SignupTerm OPTIONAL_MARKETING = new SignupTerm(
            "3",
            "TERMS_MARKETING",
            "v1.0",
            "마케팅 정보 수신 동의",
            "마케팅 약관 내용",
            false,
            false
    );

    private static final List<SignupTerm> CURRENT_TERMS = List.of(
            REQUIRED_SERVICE,
            REQUIRED_PRIVACY,
            OPTIONAL_MARKETING
    );

    @Mock
    SignupTermsQueryPort signupTermsQueryPort;

    @Mock
    TermsAuthTokenPort termsAuthTokenPort;

    @Mock
    AuthTokenGeneratorPort authTokenGeneratorPort;

    SignupTermsService signupTermsService;

    @BeforeEach
    void setUp() {
        signupTermsService = new SignupTermsService(
                signupTermsQueryPort,
                termsAuthTokenPort,
                authTokenGeneratorPort,
                new SignupTokenProperties(TERMS_AUTH_TTL)
        );

        given(signupTermsQueryPort.findLatestSignupTerms())
                .willReturn(CURRENT_TERMS);
    }

    @Test
    @DisplayName("필수 약관에 모두 동의하고 열람하면 termsAuthToken을 발급한다")
    void issuesTermsAuthTokenWhenRequiredTermsAreAgreed() {
        given(authTokenGeneratorPort.generateTermsAuthToken())
                .willReturn(TERMS_AUTH_TOKEN);

        TermsAgreementResult result = signupTermsService.checkTermsAgreement(
                command(
                        agreement("1", "v1.0", true, true),
                        agreement("2", "v1.0", true, true)
                )
        );

        assertThat(result.termsAuthToken()).isEqualTo(TERMS_AUTH_TOKEN);
        assertThat(result.expiresIn()).isEqualTo(1800L);

        ArgumentCaptor<TermsAuthTokenPayload> payloadCaptor =
                ArgumentCaptor.forClass(TermsAuthTokenPayload.class);

        verify(termsAuthTokenPort).save(
                org.mockito.ArgumentMatchers.eq(TERMS_AUTH_TOKEN),
                payloadCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(TERMS_AUTH_TTL)
        );

        assertThat(payloadCaptor.getValue().agreedTerms())
                .extracting("termsId", "version")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("1", "v1.0"),
                        org.assertj.core.groups.Tuple.tuple("2", "v1.0")
                );
    }

    @Test
    @DisplayName("필수 약관이 누락되면 ATH0006을 반환한다")
    void rejectsMissingRequiredTerm() {
        assertTermsError(
                command(agreement("1", "v1.0", true, true)),
                SignupErrorCode.REQUIRED_TERMS_NOT_AGREED
        );
    }

    @Test
    @DisplayName("필수 약관에 동의하지 않으면 ATH0006을 반환한다")
    void rejectsDisagreedRequiredTerm() {
        assertTermsError(
                command(
                        agreement("1", "v1.0", true, true),
                        agreement("2", "v1.0", false, true)
                ),
                SignupErrorCode.REQUIRED_TERMS_NOT_AGREED
        );
    }

    @Test
    @DisplayName("필수 약관을 열람하지 않으면 ATH0006을 반환한다")
    void rejectsUnreadRequiredTerm() {
        assertTermsError(
                command(
                        agreement("1", "v1.0", true, true),
                        agreement("2", "v1.0", true, false)
                ),
                SignupErrorCode.REQUIRED_TERMS_NOT_AGREED
        );
    }

    @Test
    @DisplayName("선택 약관에 동의하지 않아도 토큰을 발급한다")
    void permitsDisagreedOptionalTerm() {
        given(authTokenGeneratorPort.generateTermsAuthToken())
                .willReturn(TERMS_AUTH_TOKEN);

        TermsAgreementResult result = signupTermsService.checkTermsAgreement(
                command(
                        agreement("1", "v1.0", true, true),
                        agreement("2", "v1.0", true, true),
                        agreement("3", "v1.0", false, true)
                )
        );

        assertThat(result.termsAuthToken()).isEqualTo(TERMS_AUTH_TOKEN);

        ArgumentCaptor<TermsAuthTokenPayload> payloadCaptor =
                ArgumentCaptor.forClass(TermsAuthTokenPayload.class);
        verify(termsAuthTokenPort).save(
                org.mockito.ArgumentMatchers.eq(TERMS_AUTH_TOKEN),
                payloadCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(TERMS_AUTH_TTL)
        );
        assertThat(payloadCaptor.getValue().agreedTerms())
                .extracting(agreedTerm -> agreedTerm.termsId())
                .containsExactly("1", "2");
    }

    @Test
    @DisplayName("중복 termsId가 있으면 CMN0001을 반환한다")
    void rejectsDuplicateTermsId() {
        assertTermsError(
                command(
                        agreement("1", "v1.0", true, true),
                        agreement("1", "v1.0", true, true),
                        agreement("2", "v1.0", true, true)
                ),
                CommonErrorCode.INVALID_INPUT
        );
    }

    @Test
    @DisplayName("존재하지 않는 termsId가 있으면 CMN0001을 반환한다")
    void rejectsUnknownTermsId() {
        assertTermsError(
                command(
                        agreement("1", "v1.0", true, true),
                        agreement("2", "v1.0", true, true),
                        agreement("999", "v1.0", true, true)
                ),
                CommonErrorCode.INVALID_INPUT
        );
    }

    @Test
    @DisplayName("현재 약관과 버전이 다르면 CMN0001을 반환한다")
    void rejectsOutdatedTermsVersion() {
        assertTermsError(
                command(
                        agreement("1", "v0.9", true, true),
                        agreement("2", "v1.0", true, true)
                ),
                CommonErrorCode.INVALID_INPUT
        );
    }

    @Test
    @DisplayName("검증 실패 시 토큰을 생성하거나 Redis에 저장하지 않는다")
    void doesNotIssueTokenWhenValidationFails() {
        assertThatThrownBy(() -> signupTermsService.checkTermsAgreement(
                command(
                        agreement("1", "v1.0", true, true),
                        agreement("2", "v1.0", false, true)
                )
        )).isInstanceOf(BusinessException.class);

        verifyNoInteractions(authTokenGeneratorPort, termsAuthTokenPort);
    }

    private void assertTermsError(
            CheckTermsAgreementCommand command,
            com.shinhan.corebank.common.exception.ErrorCode expectedErrorCode
    ) {
        assertThatThrownBy(() -> signupTermsService.checkTermsAgreement(command))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode()
                ).isEqualTo(expectedErrorCode));

        verify(authTokenGeneratorPort, never()).generateTermsAuthToken();
        verify(termsAuthTokenPort, never()).save(any(), any(), any());
    }

    private CheckTermsAgreementCommand command(
            CheckTermsAgreementCommand.Agreement... agreements
    ) {
        return new CheckTermsAgreementCommand(List.of(agreements));
    }

    private CheckTermsAgreementCommand.Agreement agreement(
            String termsId,
            String version,
            boolean isAgreed,
            boolean isRead
    ) {
        return new CheckTermsAgreementCommand.Agreement(
                termsId,
                version,
                isAgreed,
                isRead
        );
    }
}
