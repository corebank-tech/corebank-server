package com.shinhan.corebank.fds.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.audit.AccessReason;
import com.shinhan.corebank.common.audit.AuditLogService;
import com.shinhan.corebank.fds.api.FdsHoldDetail;
import com.shinhan.corebank.fds.api.FdsHoldListPage;
import com.shinhan.corebank.fds.application.service.FdsAdminActionService;
import com.shinhan.corebank.fds.application.service.FdsAdminActionStore;
import com.shinhan.corebank.fds.application.service.FdsHoldDetailService;
import com.shinhan.corebank.fds.application.service.FdsHoldListService;
import com.shinhan.corebank.fds.application.service.FdsRiskAccountService;
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
        var fraudReject = action("FRAUD_REJECT", "FRAUD_CONFIRMED");
        when(store.recordCall(10L, "call-key", 7L, "CUSTOMER_CONFIRMED", "본인 확인"))
                .thenReturn(call);
        when(actions.act(10L, "approve-key", 7L, "APPROVE", null)).thenReturn(approve);
        when(actions.act(10L, "reject-key", 7L, "REJECT", "CUSTOMER_DENIED")).thenReturn(reject);
        when(actions.act(10L, "fraud-key", 7L, "FRAUD_REJECT", "FRAUD_CONFIRMED"))
                .thenReturn(fraudReject);
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
        assertThat(controller.fraudReject(10L, "fraud-key").data()).isSameAs(fraudReject);
        verify(current, org.mockito.Mockito.times(4)).getCurrentCustomerId();
    }

    @Test
    void riskAccountControllerReturnsMaskedReadModelsAndAuditsSuccessfulReads() {
        FdsRiskAccountService risks = mock(FdsRiskAccountService.class);
        CurrentCustomerProvider current = mock(CurrentCustomerProvider.class);
        AuditLogService audit = mock(AuditLogService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        var summary = new FdsRiskAccountService.RiskAccountSummary(
                1L, "088", "*******9991", "ADMIN_CONFIRMED", null, null, "risk-v1", 1);
        var detail = new FdsRiskAccountService.RiskAccountDetail(summary, List.of());
        when(current.getCurrentCustomerId()).thenReturn(7L);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(risks.findActiveAccounts()).thenReturn(List.of(summary));
        when(risks.findActiveAccount(1L)).thenReturn(detail);
        FdsRiskAccountController controller = new FdsRiskAccountController(risks, current, audit);

        assertThat(controller.findAll(AccessReason.OPERATION, request).data()).containsExactly(summary);
        assertThat(controller.find(1L, AccessReason.OPERATION, request).data()).isSameAs(detail);
        verify(audit, org.mockito.Mockito.times(2))
                .recordPersonalDataAccess(
                        org.mockito.ArgumentMatchers.eq(7L),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq("127.0.0.1"),
                        org.mockito.ArgumentMatchers.eq(true),
                        org.mockito.ArgumentMatchers.anyMap(),
                        org.mockito.ArgumentMatchers.eq(AccessReason.OPERATION));
    }

    private static FdsAdminActionStore.Action action(String type, String reason) {
        return new FdsAdminActionStore.Action(
                "action", 10L, "fp", 7L, type, reason, 1L, "NO_MATCH", null, "v1", "COMPLETED");
    }
}
