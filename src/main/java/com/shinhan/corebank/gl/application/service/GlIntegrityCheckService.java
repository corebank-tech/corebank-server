package com.shinhan.corebank.gl.application.service;

import com.shinhan.corebank.business.api.BusinessDateProvider;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.gl.api.GlTxType;
import com.shinhan.corebank.gl.application.port.in.GlIntegrityCheckUseCase;
import com.shinhan.corebank.gl.application.port.out.GlIntegrityQueryPort;
import com.shinhan.corebank.gl.application.port.out.GlIntegrityQueryPort.UnbalancedVouchers;
import com.shinhan.corebank.gl.application.port.out.LedgerTransactionReadPort;
import com.shinhan.corebank.gl.domain.GlIntegrityReport;
import com.shinhan.corebank.gl.domain.LedgerTransaction;
import com.shinhan.corebank.gl.domain.LedgerVoucherMismatch;
import com.shinhan.corebank.gl.domain.LedgerVoucherTypes;
import com.shinhan.corebank.gl.domain.VoucherTotals;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * GL 정합성 점검 (PH-28b). 전표 차대변 점검과 원장↔전표 대사를 거래일 하루 단위로 돌린다.
 *
 * <p>원장↔전표 대사는 원장 거래를 거래번호 순 청크로 읽고, 청크마다 읽기 전용 트랜잭션 하나에서 원장과 전표를 함께 읽는다.
 * 훅 B 가 원장과 전표를 같은 트랜잭션에서 쓰므로 같은 스냅샷(REPEATABLE READ)에서 읽으면 동시에 들어오는 이체를 결함으로
 * 오탐하지 않는다. 하루 전체를 한 트랜잭션으로 읽지 않는 이유는 LedgerFullReconciliationService 와 같다 — 스냅샷을 오래
 * 쥐면 undo 가 쌓여 운영 이체가 느려진다.
 *
 * <p>고아 전표(원장 없이 전표만 있는 경우)는 대상 유형 전표 수에서 짝지어진 전표 수를 빼서 센다. 전표 수를 대사보다 먼저
 * 세므로, 점검 중에 들어온 기표는 덜 세는 쪽으로만 어긋난다 — 고아가 없는데 있다고 내지는 않는다. 그래도 마감 전 거래일은
 * 덜 셀 수 있어 참고값으로 표시한다.
 */
@Slf4j
@Service
public class GlIntegrityCheckService implements GlIntegrityCheckUseCase {

    static final int LIST_LIMIT = 100;

    private final LedgerTransactionReadPort ledgerTransactionReadPort;
    private final GlIntegrityQueryPort glIntegrityQueryPort;
    private final BusinessDateProvider businessDateProvider;
    private final TransactionTemplate readOnlyTransactionTemplate;
    private final int chunkSize;

    public GlIntegrityCheckService(
            LedgerTransactionReadPort ledgerTransactionReadPort,
            GlIntegrityQueryPort glIntegrityQueryPort,
            BusinessDateProvider businessDateProvider,
            PlatformTransactionManager transactionManager,
            @Value("${app.gl-integrity-check.chunk-size:1000}") int chunkSize) {
        this.ledgerTransactionReadPort = ledgerTransactionReadPort;
        this.glIntegrityQueryPort = glIntegrityQueryPort;
        this.businessDateProvider = businessDateProvider;
        this.readOnlyTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTransactionTemplate.setReadOnly(true);
        this.chunkSize = chunkSize;
    }

    @Override
    public GlIntegrityReport check(LocalDate tradeDate) {
        if (tradeDate == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
        long startedAt = System.nanoTime();
        boolean closedTradeDate = tradeDate.isBefore(businessDateProvider.today());
        Long voucherCount = readOnlyTransactionTemplate.execute(
                status -> glIntegrityQueryPort.countVouchers(tradeDate, LedgerVoucherTypes.RECONCILED_VOUCHER_TYPES));
        UnbalancedVouchers unbalanced = readOnlyTransactionTemplate.execute(
                status -> glIntegrityQueryPort.findUnbalancedVouchers(tradeDate, LIST_LIMIT));

        Reconciliation reconciliation = new Reconciliation();
        String cursor = null;
        while (true) {
            String after = cursor;
            cursor = readOnlyTransactionTemplate.execute(status -> reconcileChunk(tradeDate, after, reconciliation));
            if (cursor == null) {
                break;
            }
        }

        GlIntegrityReport report = new GlIntegrityReport(
                tradeDate,
                reconciliation.checked,
                reconciliation.excluded,
                unbalanced.vouchers(),
                unbalanced.totalCount(),
                reconciliation.mismatches,
                reconciliation.mismatchCount,
                Math.max(0L, voucherCount - reconciliation.matched),
                closedTradeDate);
        log.info(
                "GL 정합성 점검 종료 - tradeDate={}, checked={}, excluded={}, unbalancedVouchers={}, mismatches={},"
                        + " orphanVouchers={}, orphanFinal={}, elapsedMs={}",
                tradeDate,
                report.checkedTransactionCount(),
                report.excludedTransactionCounts(),
                report.unbalancedVoucherCount(),
                report.ledgerVoucherMismatchCount(),
                report.orphanVoucherCount(),
                report.orphanVoucherCountFinal(),
                (System.nanoTime() - startedAt) / 1_000_000);
        return report;
    }

    /** 청크 하나를 대사하고 마지막 거래번호를 돌려준다. 더 읽을 거래가 없으면 {@code null}. */
    private String reconcileChunk(LocalDate tradeDate, String after, Reconciliation reconciliation) {
        List<LedgerTransaction> chunk = ledgerTransactionReadPort.findByTradeDate(tradeDate, after, chunkSize);
        if (chunk.isEmpty()) {
            return null;
        }
        Map<String, GlTxType> targets = new HashMap<>();
        for (LedgerTransaction transaction : chunk) {
            Optional<GlTxType> voucherType = LedgerVoucherTypes.voucherTypeOf(transaction.ledgerTransactionType());
            if (voucherType.isPresent()) {
                targets.put(transaction.transactionNumber(), voucherType.get());
            } else {
                reconciliation.excluded.merge(String.valueOf(transaction.ledgerTransactionType()), 1L, Long::sum);
            }
        }
        if (!targets.isEmpty()) {
            Map<VoucherKey, VoucherTotals> vouchers = new HashMap<>();
            for (VoucherTotals totals : glIntegrityQueryPort.sumVoucherTotals(
                    tradeDate, new HashSet<>(targets.values()), targets.keySet())) {
                vouchers.put(new VoucherKey(totals.txType(), totals.referenceKey()), totals);
            }
            for (LedgerTransaction transaction : chunk) {
                GlTxType voucherType = targets.get(transaction.transactionNumber());
                if (voucherType == null) {
                    continue;
                }
                reconciliation.checked++;
                VoucherTotals voucher = vouchers.get(new VoucherKey(voucherType, transaction.transactionNumber()));
                if (voucher != null) {
                    reconciliation.matched++;
                }
                LedgerVoucherMismatch.judge(transaction, voucher).ifPresent(reconciliation::add);
            }
        }
        // 청크가 limit 보다 작으면 마지막이다. 한 번 더 읽어 빈 결과를 확인하지 않는다.
        return chunk.size() < chunkSize ? null : chunk.getLast().transactionNumber();
    }

    private record VoucherKey(GlTxType txType, String referenceKey) {}

    private static final class Reconciliation {
        private long checked;
        private long matched;
        private final Map<String, Long> excluded = new TreeMap<>();
        private final List<LedgerVoucherMismatch> mismatches = new ArrayList<>();
        private long mismatchCount;

        private void add(LedgerVoucherMismatch mismatch) {
            mismatchCount++;
            if (mismatches.size() < LIST_LIMIT) {
                mismatches.add(mismatch);
            }
        }
    }
}
