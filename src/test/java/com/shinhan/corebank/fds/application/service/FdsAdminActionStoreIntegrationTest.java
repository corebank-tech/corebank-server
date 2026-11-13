package com.shinhan.corebank.fds.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.fds.application.port.out.FdsAdminReadPort;
import com.shinhan.corebank.fds.application.service.FdsRiskAccountService.RiskRow;
import com.shinhan.corebank.transfer.api.TransferHoldForFds;
import com.shinhan.corebank.transfer.api.TransferHoldUseCase;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class FdsAdminActionStoreIntegrationTest extends IntegrationTestSupport {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T03:00:00Z"), ZoneOffset.UTC);

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FdsAdminReadPort reads;

    private TransferHoldUseCase holds;
    private FdsRiskAccountService risks;
    private FdsAdminActionStore store;

    @BeforeEach
    void setUp() {
        holds = mock(TransferHoldUseCase.class);
        risks = new FdsRiskAccountService(jdbc, CLOCK);
        store = new FdsAdminActionStore(jdbc, holds, risks, CLOCK);
    }

    @Test
    void callAndApprovalAreIdempotentAndReconcileToCompleted() {
        long holdId = holdId();
        when(holds.findForFds(holdId)).thenReturn(hold(holdId, "HELD", "99999999999", null, null));
        activate("110039200000");
        String callKey = key();

        var call = store.recordCall(holdId, callKey, 7L, "CUSTOMER_CONFIRMED", "본인 확인");
        assertThat(store.recordCall(holdId, callKey, 7L, "CUSTOMER_CONFIRMED", "본인 확인"))
                .isEqualTo(call);
        assertThatThrownBy(() -> store.recordCall(holdId, callKey, 7L, "NO_ANSWER", "본인 확인"))
                .isInstanceOf(BusinessException.class);

        String actionKey = key();
        var pending = store.prepare(holdId, actionKey, 7L, "APPROVE", null);
        assertThat(pending.status()).isEqualTo("PENDING");
        assertThat(store.prepare(holdId, actionKey, 7L, "APPROVE", null)).isEqualTo(pending);

        var completed = store.reconcile(pending, hold(holdId, "EXECUTED", "99999999999", 7L, null));
        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM fds_admin_action_audit WHERE action_id=?",
                        Integer.class,
                        pending.actionId()))
                .isEqualTo(1);
        assertThat(reads.findCallRecords(holdId)).singleElement().satisfies(stored -> {
            assertThat(stored.callResult()).isEqualTo("CUSTOMER_CONFIRMED");
            assertThat(stored.selectedByAction()).isTrue();
        });
        assertThat(reads.findLatestAction(holdId).actionStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void matchedRiskAllowsOnlyRiskMatchRejectionWithoutCall() {
        long holdId = holdId();
        String riskyAccount = "110039200000";
        activate(riskyAccount);
        when(holds.findForFds(holdId)).thenReturn(hold(holdId, "HELD", riskyAccount, null, null));

        var pending = store.prepare(holdId, key(), 8L, "REJECT", "RISK_ACCOUNT_MATCH");
        assertThat(pending.riskStatus()).isEqualTo("MATCH");
        assertThat(pending.callRecordId()).isNull();

        var completed = store.reconcile(pending, hold(holdId, "REJECTED", riskyAccount, 8L, "RISK_ACCOUNT_MATCH"));
        assertThat(completed.status()).isEqualTo("COMPLETED");

        long another = holdId();
        when(holds.findForFds(another)).thenReturn(hold(another, "HELD", riskyAccount, null, null));
        assertThatThrownBy(() -> store.prepare(another, key(), 8L, "APPROVE", null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void noMatchRequiresCompatibleCallAndMismatchedP4ResultConflicts() {
        long holdId = holdId();
        activate("110039200000");
        when(holds.findForFds(holdId)).thenReturn(hold(holdId, "HELD", "99999999999", null, null));

        assertThatThrownBy(() -> store.prepare(holdId, key(), 7L, "REJECT", "CUSTOMER_DENIED"))
                .isInstanceOf(BusinessException.class);
        store.recordCall(holdId, key(), 7L, "CUSTOMER_DENIED", "고객 거절");
        var pending = store.prepare(holdId, key(), 7L, "REJECT", "CUSTOMER_DENIED");

        var conflict = store.reconcile(pending, hold(holdId, "REJECTED", "99999999999", 99L, "CUSTOMER_DENIED"));
        assertThat(conflict.status()).isEqualTo("CONFLICT");
        assertThat(store.pending()).doesNotContain(conflict);
    }

    @Test
    void rejectsInvalidCallActionAndNonHeldState() {
        long holdId = holdId();
        when(holds.findForFds(holdId)).thenReturn(hold(holdId, "EXPIRED", "99999999999", null, null));
        assertThatThrownBy(() -> store.recordCall(holdId, key(), 7L, "UNKNOWN", "사유"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> store.recordCall(holdId, key(), 7L, "NO_ANSWER", " "))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> store.prepare(holdId, key(), 7L, "APPROVE", null))
                .isInstanceOf(BusinessException.class);
    }

    private void activate(String account) {
        String version = "admin-" + UUID.randomUUID().toString().substring(0, 8);
        risks.loadAndActivate(version, 1, "d".repeat(64), List.of(new RiskRow("088", account, "합성")));
    }

    private static TransferHoldForFds hold(
            long holdId, String status, String account, Long actorId, String reasonCode) {
        return new TransferHoldForFds(
                holdId,
                20L,
                null,
                status,
                LocalDateTime.now(CLOCK),
                1000L,
                account,
                "A",
                30L,
                "088",
                "ON_APPROVAL",
                actorId,
                reasonCode,
                status.equals("HELD") ? null : LocalDateTime.now(CLOCK));
    }

    private static long holdId() {
        return 992_800_000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 100_000);
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }
}
