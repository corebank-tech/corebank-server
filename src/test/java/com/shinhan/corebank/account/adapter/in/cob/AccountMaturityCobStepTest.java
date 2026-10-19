package com.shinhan.corebank.account.adapter.in.cob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.shinhan.corebank.account.application.port.in.AccountMaturityUseCase;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountMaturityCobStepTest {

    private static final LocalDate COB_DATE = LocalDate.of(2026, 10, 23);

    @Mock
    AccountMaturityUseCase accountMaturityUseCase;

    @Test
    @DisplayName("만기 감지 스텝은 COB 체인 순서 3번이다")
    void order_isThree() {
        AccountMaturityCobStep step = new AccountMaturityCobStep(accountMaturityUseCase);

        assertThat(step.order()).isEqualTo(3);
        assertThat(step.name()).isEqualTo("account-maturity");
    }

    @Test
    @DisplayName("러너가 전달한 COB_DATE를 만기 감지 유스케이스에 그대로 전달한다")
    void run_delegatesCobDate() {
        AccountMaturityCobStep step = new AccountMaturityCobStep(accountMaturityUseCase);

        step.run(COB_DATE);

        verify(accountMaturityUseCase).runInCob(COB_DATE);
        verifyNoMoreInteractions(accountMaturityUseCase);
    }
}
