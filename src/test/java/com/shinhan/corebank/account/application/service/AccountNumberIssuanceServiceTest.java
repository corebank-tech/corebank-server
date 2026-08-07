package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.domain.exception.AccountErrorCode;
import com.shinhan.corebank.account.application.port.out.AccountNumberSequencePort;
import com.shinhan.corebank.account.domain.AccountNumberPolicy;
import com.shinhan.corebank.account.domain.AccountNumberSequence;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("계좌번호 채번 서비스 단위 테스트")
class AccountNumberIssuanceServiceTest {

    @Mock
    private AccountNumberSequencePort sequencePort;

    @InjectMocks
    private AccountNumberIssuanceService service;

    @Test
    @DisplayName("입출금계좌의 다음 계좌번호를 발급하고 채번 정보를 갱신한다")
    void issuesDemandDepositAccountNumber() {
        // given
        AccountNumberSequence sequence =
            createDemandDepositSequence(0L);

        given(sequencePort.findForUpdate(
            AccountNumberPolicy.BANK_CODE,
            AccountType.DEMAND_DEPOSIT,
            null
        )).willReturn(Optional.of(sequence));

        // when
        String result = service.issue(
            AccountType.DEMAND_DEPOSIT,
            null
        );

        // then
        assertThat(result).isEqualTo("088100000001");
        assertThat(sequence.getLastSequence()).isEqualTo(1L);

        verify(sequencePort).update(sequence);
    }

    @Test
    @DisplayName("입출금계좌에 상품 ID가 전달되면 CMN0001 오류가 발생한다")
    void throwsInvalidInputWhenDemandDepositHasProductId() {
        assertThatThrownBy(() ->
            service.issue(
                AccountType.DEMAND_DEPOSIT,
                1L
            )
        )
            .isInstanceOf(BusinessException.class)
            .satisfies(throwable -> {
                BusinessException exception =
                    (BusinessException) throwable;

                assertThat(exception.getErrorCode())
                    .isEqualTo(CommonErrorCode.INVALID_INPUT);
            });

        verifyNoInteractions(sequencePort);
    }

    @Test
    @DisplayName("정기예금에 상품 ID가 없으면 CMN0001 오류가 발생한다")
    void throwsInvalidInputWhenTimeDepositHasNoProductId() {
        assertThatThrownBy(() ->
            service.issue(
                AccountType.TIME_DEPOSIT,
                null
            )
        )
            .isInstanceOf(BusinessException.class)
            .satisfies(throwable -> {
                BusinessException exception =
                    (BusinessException) throwable;

                assertThat(exception.getErrorCode())
                    .isEqualTo(CommonErrorCode.INVALID_INPUT);
            });

        verifyNoInteractions(sequencePort);
    }

    @Test
    @DisplayName("채번 기준 행이 없으면 ACC9001 오류가 발생한다")
    void throwsSequenceNotFoundWhenSequenceDoesNotExist() {
        // given
        given(sequencePort.findForUpdate(
            AccountNumberPolicy.BANK_CODE,
            AccountType.DEMAND_DEPOSIT,
            null
        )).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
            service.issue(
                AccountType.DEMAND_DEPOSIT,
                null
            )
        )
            .isInstanceOf(BusinessException.class)
            .satisfies(throwable -> {
                BusinessException exception =
                    (BusinessException) throwable;

                assertThat(exception.getErrorCode())
                    .isEqualTo(
                        AccountErrorCode.ACCOUNT_NUMBER_SEQUENCE_NOT_FOUND
                    );
            });
    }

    @Test
    @DisplayName("일련번호가 소진되면 ACC0303 오류가 발생하고 갱신하지 않는다")
    void throwsSequenceExhaustedWhenSequenceIsExhausted() {
        // given
        AccountNumberSequence sequence =
            createDemandDepositSequence(
                AccountNumberPolicy.MAX_SEQUENCE
            );

        given(sequencePort.findForUpdate(
            AccountNumberPolicy.BANK_CODE,
            AccountType.DEMAND_DEPOSIT,
            null
        )).willReturn(Optional.of(sequence));

        // when & then
        assertThatThrownBy(() ->
            service.issue(
                AccountType.DEMAND_DEPOSIT,
                null
            )
        )
            .isInstanceOf(BusinessException.class)
            .satisfies(throwable -> {
                BusinessException exception =
                    (BusinessException) throwable;

                assertThat(exception.getErrorCode())
                    .isEqualTo(
                        AccountErrorCode.ACCOUNT_NUMBER_SEQUENCE_EXHAUSTED
                    );
            });

        assertThat(sequence.getLastSequence())
            .isEqualTo(AccountNumberPolicy.MAX_SEQUENCE);

        verify(sequencePort, never()).update(sequence);
    }

    private AccountNumberSequence createDemandDepositSequence(
        long lastSequence
    ) {
        return AccountNumberSequence.reconstitute(
            1L,
            AccountNumberPolicy.BANK_CODE,
            AccountType.DEMAND_DEPOSIT,
            null,
            "10",
            lastSequence
        );
    }
}