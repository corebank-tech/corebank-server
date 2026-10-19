package com.shinhan.corebank.account.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.business.api.BusinessDateProvider;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountMaturityServiceTest {

    private static final LocalDate COB_DATE = LocalDate.of(2026, 10, 23);

    private static final LocalDate MATURITY_BASE_DATE = LocalDate.of(2026, 10, 26);

    @Mock
    BusinessDateProvider businessDateProvider;

    @Mock
    AccountPersistencePort accountPersistencePort;

    @Mock
    AccountMaturityTransitionService transitionService;

    @InjectMocks
    AccountMaturityService service;

    @Test
    @DisplayName("COB_DATE의 다음 영업일로 만기 후보를 페이지 순회한다")
    void runInCob_paginatesWithNextBusinessDay() {
        when(businessDateProvider.nextBusinessDay(COB_DATE)).thenReturn(MATURITY_BASE_DATE);

        when(accountPersistencePort.findMaturityCandidateIdsAfter(0L, MATURITY_BASE_DATE, 500))
                .thenReturn(List.of(101L, 102L));

        when(accountPersistencePort.findMaturityCandidateIdsAfter(102L, MATURITY_BASE_DATE, 500))
                .thenReturn(List.of(103L));

        when(accountPersistencePort.findMaturityCandidateIdsAfter(103L, MATURITY_BASE_DATE, 500))
                .thenReturn(List.of());

        when(transitionService.transition(List.of(101L, 102L), MATURITY_BASE_DATE))
                .thenReturn(2);

        when(transitionService.transition(List.of(103L), MATURITY_BASE_DATE)).thenReturn(1);

        service.runInCob(COB_DATE);

        InOrder order = inOrder(accountPersistencePort, transitionService);

        order.verify(accountPersistencePort).findMaturityCandidateIdsAfter(0L, MATURITY_BASE_DATE, 500);

        order.verify(transitionService).transition(List.of(101L, 102L), MATURITY_BASE_DATE);

        order.verify(accountPersistencePort).findMaturityCandidateIdsAfter(102L, MATURITY_BASE_DATE, 500);

        order.verify(transitionService).transition(List.of(103L), MATURITY_BASE_DATE);

        order.verify(accountPersistencePort).findMaturityCandidateIdsAfter(103L, MATURITY_BASE_DATE, 500);

        verify(businessDateProvider, never()).today();
    }

    @Test
    @DisplayName("만기 후보가 없으면 상태 전이 서비스를 호출하지 않는다")
    void runInCob_noCandidates() {
        when(businessDateProvider.nextBusinessDay(COB_DATE)).thenReturn(MATURITY_BASE_DATE);

        when(accountPersistencePort.findMaturityCandidateIdsAfter(0L, MATURITY_BASE_DATE, 500))
                .thenReturn(List.of());

        service.runInCob(COB_DATE);

        verifyNoInteractions(transitionService);
    }

    @Test
    @DisplayName("상태 전이 실패는 예외를 전파하고 다음 페이지를 처리하지 않는다")
    void runInCob_propagatesFailure() {
        when(businessDateProvider.nextBusinessDay(COB_DATE)).thenReturn(MATURITY_BASE_DATE);

        when(accountPersistencePort.findMaturityCandidateIdsAfter(0L, MATURITY_BASE_DATE, 500))
                .thenReturn(List.of(101L));

        IllegalStateException failure = new IllegalStateException("test failure");

        when(transitionService.transition(List.of(101L), MATURITY_BASE_DATE)).thenThrow(failure);

        assertThatThrownBy(() -> service.runInCob(COB_DATE)).isSameAs(failure);

        verify(accountPersistencePort, never()).findMaturityCandidateIdsAfter(101L, MATURITY_BASE_DATE, 500);
    }

    @Test
    @DisplayName("COB_DATE가 null이면 만기 감지를 시작하지 않는다")
    void runInCob_nullDate() {
        assertThatThrownBy(() -> service.runInCob(null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING);

        verifyNoInteractions(businessDateProvider, accountPersistencePort, transitionService);
    }
}
