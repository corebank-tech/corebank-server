package com.shinhan.corebank.fds.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.fds.application.service.FdsAdminActionStore.Action;
import com.shinhan.corebank.transfer.api.TransferHoldForFds;
import com.shinhan.corebank.transfer.api.TransferHoldUseCase;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FdsAdminActionServiceTest {
    @Mock
    FdsAdminActionStore store;

    @Mock
    TransferHoldUseCase holds;

    private FdsAdminActionService service;

    @BeforeEach
    void setUp() {
        service = new FdsAdminActionService(store, holds);
    }

    @Test
    void responseLossIsRecoveredFromMatchingP4Result() {
        Action pending = action("PENDING", "REJECT", "CUSTOMER_DENIED");
        Action completed = action("COMPLETED", "REJECT", "CUSTOMER_DENIED");
        when(store.prepare(10L, "key", 7L, "REJECT", "CUSTOMER_DENIED")).thenReturn(pending);
        when(holds.findForFds(10L)).thenReturn(held(), rejected());
        org.mockito.Mockito.doThrow(new RuntimeException("lost")).when(holds).reject(10L, 7L, "CUSTOMER_DENIED");
        when(store.reconcile(pending, rejected())).thenReturn(completed);

        assertThat(service.act(10L, "key", 7L, "REJECT", "CUSTOMER_DENIED").status())
                .isEqualTo("COMPLETED");
    }

    @Test
    void expiredApprovalIsMappedToFdsConflictWithoutRetry() {
        Action pending = action("PENDING", "APPROVE", null);
        when(store.prepare(10L, "key", 7L, "APPROVE", null)).thenReturn(pending);
        when(holds.findForFds(10L)).thenReturn(expired());
        when(store.reconcile(pending, expired())).thenReturn(action("CONFLICT", "APPROVE", null));

        assertThatThrownBy(() -> service.act(10L, "key", 7L, "APPROVE", null)).isInstanceOf(BusinessException.class);
        verify(holds, never()).release(10L, 7L);
    }

    private static Action action(String status, String type, String reason) {
        return new Action("action", 10L, "fingerprint", 7L, type, reason, 1L, "NO_MATCH", null, "v1", status);
    }

    private static TransferHoldForFds held() {
        return hold("HELD", null, null);
    }

    private static TransferHoldForFds rejected() {
        return hold("REJECTED", 7L, "CUSTOMER_DENIED");
    }

    private static TransferHoldForFds expired() {
        return hold("EXPIRED", null, null);
    }

    private static TransferHoldForFds hold(String status, Long actorId, String reasonCode) {
        return new TransferHoldForFds(
                10L,
                20L,
                null,
                status,
                LocalDateTime.of(2026, 11, 12, 12, 0),
                1000L,
                "12345678",
                "A",
                1L,
                "088",
                "ON_APPROVAL",
                actorId,
                reasonCode,
                null,
                3,
                1_500_000L,
                true,
                true);
    }
}
