package com.shinhan.corebank.fds.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class FdsAdminActionStoreIntegrationTest extends IntegrationTestSupport {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T03:00:00Z"), ZoneOffset.UTC);

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FdsAdminReadPort reads;

    @MockitoSpyBean
    TransferHoldUseCase holds;

    @Autowired
    FdsRiskAccountService risks;

    @Autowired
    FdsAdminActionStore store;

    @Test
    void callAndApprovalAreIdempotentAndReconcileToCompleted() {
        long holdId = holdId();
        doReturn(hold(holdId, "HELD", "99999999999", null, null)).when(holds).findForFds(holdId);
        activate("110039200000");
        String callKey = key();

        var call = store.recordCall(holdId, callKey, 7L, "CUSTOMER_CONFIRMED", "본인 확인");
        assertEquivalentCall(store.recordCall(holdId, callKey, 7L, "CUSTOMER_CONFIRMED", "본인 확인"), call);
        assertThatThrownBy(() -> store.recordCall(holdId, callKey, 7L, "NO_ANSWER", "본인 확인"))
                .isInstanceOf(BusinessException.class);

        String actionKey = key();
        var pending = store.prepare(holdId, actionKey, 7L, "APPROVE", null);
        assertThat(pending.status()).isEqualTo("PENDING");
        assertThat(store.prepare(holdId, actionKey, 7L, "APPROVE", null)).isEqualTo(pending);

        var completed = store.reconcile(pending, hold(holdId, "EXECUTED", "99999999999", 7L, null));
        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(store.reconcile(completed, hold(holdId, "EXECUTED", "99999999999", 7L, null)))
                .isEqualTo(completed);
        doReturn(hold(holdId, "EXECUTED", "99999999999", 7L, null)).when(holds).findForFds(holdId);
        assertThat(store.prepare(holdId, actionKey, 7L, "APPROVE", null)).isEqualTo(completed);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM fds_admin_action_audit WHERE action_id=?",
                        Integer.class,
                        pending.actionId()))
                .isEqualTo(1);
        assertThat(reads.findCallRecords(holdId)).singleElement().satisfies(stored -> {
            assertThat(stored.callResult()).isEqualTo("CUSTOMER_CONFIRMED");
            assertThat(stored.selectedByAction()).isTrue();
        });
        assertThat(reads.findLatestCallRecord(holdId)).satisfies(stored -> {
            assertThat(stored.callResult()).isEqualTo("CUSTOMER_CONFIRMED");
            assertThat(stored.selectedByAction()).isTrue();
        });
        assertThat(reads.findLatestCallRecord(holdId())).isNull();
        assertThat(reads.findLatestAction(holdId).actionStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void matchedRiskAllowsOnlyRiskMatchRejectionWithoutCall() {
        long holdId = holdId();
        String riskyAccount = "110039200000";
        activate(riskyAccount);
        doReturn(hold(holdId, "HELD", riskyAccount, null, null)).when(holds).findForFds(holdId);

        var pending = store.prepare(holdId, key(), 8L, "REJECT", "RISK_ACCOUNT_MATCH");
        assertThat(pending.riskStatus()).isEqualTo("MATCH");
        assertThat(pending.callRecordId()).isNull();

        var completed = store.reconcile(pending, hold(holdId, "REJECTED", riskyAccount, 8L, "RISK_ACCOUNT_MATCH"));
        assertThat(completed.status()).isEqualTo("COMPLETED");

        long another = holdId();
        doReturn(hold(another, "HELD", riskyAccount, null, null)).when(holds).findForFds(another);
        assertThatThrownBy(() -> store.prepare(another, key(), 8L, "APPROVE", null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void noMatchRequiresCompatibleCallAndMismatchedP4ResultConflicts() {
        long holdId = holdId();
        activate("110039200000");
        doReturn(hold(holdId, "HELD", "99999999999", null, null)).when(holds).findForFds(holdId);

        assertThatThrownBy(() -> store.prepare(holdId, key(), 7L, "REJECT", "CUSTOMER_DENIED"))
                .isInstanceOf(BusinessException.class);
        store.recordCall(holdId, key(), 7L, "CUSTOMER_DENIED", "고객 거절");
        assertThatThrownBy(() -> store.prepare(holdId, key(), 7L, "REJECT", "RISK_ACCOUNT_MATCH"))
                .isInstanceOf(BusinessException.class);
        var pending = store.prepare(holdId, key(), 7L, "REJECT", "CUSTOMER_DENIED");

        var conflict = store.reconcile(pending, hold(holdId, "REJECTED", "99999999999", 99L, "CUSTOMER_DENIED"));
        assertThat(conflict.status()).isEqualTo("CONFLICT");
        assertThat(store.pending()).doesNotContain(conflict);
    }

    @Test
    void executingP4StateKeepsActionPendingWithoutAudit() {
        long holdId = holdId();
        activate("110039200000");
        doReturn(hold(holdId, "HELD", "99999999999", null, null)).when(holds).findForFds(holdId);
        store.recordCall(holdId, key(), 7L, "CUSTOMER_CONFIRMED", "본인 확인");
        var pending = store.prepare(holdId, key(), 7L, "APPROVE", null);

        var stillPending = store.reconcile(pending, hold(holdId, "EXECUTING", "99999999999", 7L, null));

        assertThat(stillPending.status()).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM fds_admin_action_audit WHERE action_id=?",
                        Integer.class,
                        pending.actionId()))
                .isZero();
    }

    @Test
    void concurrentIdempotentCallsConvergeToOneStoredResult() throws Exception {
        long holdId = holdId();
        doReturn(hold(holdId, "HELD", "99999999999", null, null)).when(holds).findForFds(holdId);
        String idempotencyKey = key();
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first =
                    CompletableFuture.supplyAsync(() -> recordAfterBarrier(barrier, holdId, idempotencyKey), executor);
            var second =
                    CompletableFuture.supplyAsync(() -> recordAfterBarrier(barrier, holdId, idempotencyKey), executor);

            assertEquivalentCall(first.get(), second.get());
        }
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM fds_admin_call_record WHERE idempotency_key=?",
                        Integer.class,
                        idempotencyKey))
                .isEqualTo(1);
    }

    @Test
    void concurrentIdempotentActionsConvergeToOnePendingResult() throws Exception {
        long holdId = holdId();
        activate("110039200000");
        doReturn(hold(holdId, "HELD", "99999999999", null, null)).when(holds).findForFds(holdId);
        store.recordCall(holdId, key(), 7L, "CUSTOMER_CONFIRMED", "본인 확인");
        String idempotencyKey = key();
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first =
                    CompletableFuture.supplyAsync(() -> prepareAfterBarrier(barrier, holdId, idempotencyKey), executor);
            var second =
                    CompletableFuture.supplyAsync(() -> prepareAfterBarrier(barrier, holdId, idempotencyKey), executor);

            assertThat(first.get()).isEqualTo(second.get());
        }
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM fds_admin_action WHERE idempotency_key=?", Integer.class, idempotencyKey))
                .isEqualTo(1);
    }

    @Test
    void rejectsInvalidCallActionAndNonHeldState() {
        long holdId = holdId();
        doReturn(hold(holdId, "EXPIRED", "99999999999", null, null)).when(holds).findForFds(holdId);
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

    private FdsAdminActionStore.CallRecord recordAfterBarrier(
            CyclicBarrier barrier, long holdId, String idempotencyKey) {
        try {
            barrier.await();
            return store.recordCall(holdId, idempotencyKey, 7L, "CUSTOMER_CONFIRMED", "본인 확인");
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private FdsAdminActionStore.Action prepareAfterBarrier(CyclicBarrier barrier, long holdId, String idempotencyKey) {
        try {
            barrier.await();
            return store.prepare(holdId, idempotencyKey, 7L, "APPROVE", null);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
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
                status.equals("HELD") ? null : LocalDateTime.now(CLOCK),
                3,
                1_500_000L,
                true,
                true);
    }

    private static long holdId() {
        return 992_800_000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 100_000);
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private static void assertEquivalentCall(
            FdsAdminActionStore.CallRecord actual, FdsAdminActionStore.CallRecord expected) {
        assertThat(actual.callRecordId()).isEqualTo(expected.callRecordId());
        assertThat(actual.fingerprint()).isEqualTo(expected.fingerprint());
        assertThat(actual.callResult()).isEqualTo(expected.callResult());
        assertThat(actual.recordedAt().truncatedTo(ChronoUnit.MICROS))
                .isEqualTo(expected.recordedAt().truncatedTo(ChronoUnit.MICROS));
    }
}
