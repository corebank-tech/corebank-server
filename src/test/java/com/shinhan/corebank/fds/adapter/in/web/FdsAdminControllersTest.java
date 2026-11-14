package com.shinhan.corebank.fds.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.audit.AccessReason;
import com.shinhan.corebank.fds.api.FdsHoldDetail;
import com.shinhan.corebank.fds.api.FdsHoldListPage;
import com.shinhan.corebank.fds.application.service.FdsAdminActionService;
import com.shinhan.corebank.fds.application.service.FdsAdminActionStore;
import com.shinhan.corebank.fds.application.service.FdsHoldDetailService;
import com.shinhan.corebank.fds.application.service.FdsHoldListService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class FdsAdminControllersTest {
    @Test
    void holdControllerPassesAuthenticatedAdminAndAuditContext() {
        FdsHoldDetailService details = mock(FdsHoldDetailService.class);
        FdsHoldListService lists = mock(FdsHoldListService.class);
        CurrentCustomerProvider current = mock(CurrentCustomerProvider.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        FdsHoldDetail detail = mock(FdsHoldDetail.class);
        FdsHoldListPage page = new FdsHoldListPage(List.of(), 0, 20, 0, 0);
        when(current.getCurrentCustomerId()).thenReturn(7L);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(details.get(10L, 7L, "127.0.0.1", AccessReason.OPERATION)).thenReturn(detail);
        when(lists.find(0, 20, "HELD", "A", "NO_MATCH", "RUNNING", 7L, "127.0.0.1", AccessReason.OPERATION))
                .thenReturn(page);
        FdsAdminHoldController controller = new FdsAdminHoldController(details, lists, current);

        assertThat(controller.get(10L, AccessReason.OPERATION, request).data()).isSameAs(detail);
        assertThat(controller
                        .list(0, 20, "HELD", "A", "NO_MATCH", "RUNNING", AccessReason.OPERATION, request)
                        .data())
                .isSameAs(page);
    }

    @Test
    void actionControllerUsesAuthenticatedActorForCallApproveAndReject() {
        FdsAdminActionStore store = mock(FdsAdminActionStore.class);
        FdsAdminActionService actions = mock(FdsAdminActionService.class);
        CurrentCustomerProvider current = mock(CurrentCustomerProvider.class);
        when(current.getCurrentCustomerId()).thenReturn(7L);
        var call = new FdsAdminActionStore.CallRecord(1L, "fp", "CUSTOMER_CONFIRMED", null);
        var approve = action("APPROVE", null);
        var reject = action("REJECT", "CUSTOMER_DENIED");
        when(store.recordCall(10L, "call-key", 7L, "CUSTOMER_CONFIRMED", "본인 확인"))
                .thenReturn(call);
        when(actions.act(10L, "approve-key", 7L, "APPROVE", null)).thenReturn(approve);
        when(actions.act(10L, "reject-key", 7L, "REJECT", "CUSTOMER_DENIED")).thenReturn(reject);
        FdsAdminActionController controller = new FdsAdminActionController(store, actions, current);

        assertThat(controller
                        .call(
                                10L,
                                "call-key",
                                AccessReason.OPERATION,
                                new FdsAdminActionController.CallRequest("CUSTOMER_CONFIRMED", "본인 확인"))
                        .data())
                .isSameAs(call);
        assertThat(controller.approve(10L, "approve-key").data()).isSameAs(approve);
        assertThat(controller
                        .reject(10L, "reject-key", new FdsAdminActionController.RejectRequest("CUSTOMER_DENIED"))
                        .data())
                .isSameAs(reject);
        verify(current, org.mockito.Mockito.times(3)).getCurrentCustomerId();
    }

    private static FdsAdminActionStore.Action action(String type, String reason) {
        return new FdsAdminActionStore.Action(
                "action", 10L, "fp", 7L, type, reason, 1L, "NO_MATCH", null, "v1", "COMPLETED");
    }
}
