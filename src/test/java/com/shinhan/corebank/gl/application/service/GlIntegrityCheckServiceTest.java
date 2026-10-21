package com.shinhan.corebank.gl.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.shinhan.corebank.business.api.BusinessDateGate;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.gl.api.GlTxType;
import com.shinhan.corebank.gl.application.port.out.GlIntegrityQueryPort;
import com.shinhan.corebank.gl.application.port.out.GlIntegrityQueryPort.UnbalancedVouchers;
import com.shinhan.corebank.gl.application.port.out.LedgerTransactionReadPort;
import com.shinhan.corebank.gl.domain.GlIntegrityReport;
import com.shinhan.corebank.gl.domain.LedgerTransaction;
import com.shinhan.corebank.gl.domain.LedgerVoucherMismatch;
import com.shinhan.corebank.gl.domain.LedgerVoucherMismatch.Kind;
import com.shinhan.corebank.gl.domain.UnbalancedVoucher;
import com.shinhan.corebank.gl.domain.VoucherTotals;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
@DisplayName("GlIntegrityCheckService 단위 테스트")
class GlIntegrityCheckServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);
    private static final int CHUNK_SIZE = 2;

    @Mock
    LedgerTransactionReadPort ledgerPort;

    @Mock
    GlIntegrityQueryPort glPort;

    @Mock
    PlatformTransactionManager transactionManager;

    @Mock
    BusinessDateGate businessDateGate;

    GlIntegrityCheckService service;

    @BeforeEach
    void setUp() {
        service = new GlIntegrityCheckService(ledgerPort, glPort, businessDateGate, transactionManager, CHUNK_SIZE);
    }

    @Test
    @DisplayName("청크를 거래번호 커서로 끝까지 읽고, 대상 유형만 대사하며 나머지는 유형별 건수로 센다")
    void walksChunksAndCountsExcluded() {
        givenBusinessDateAndVoucherCount(DAY.plusDays(1), 0L);
        given(glPort.findUnbalancedVouchers(DAY, GlIntegrityCheckService.LIST_LIMIT))
                .willReturn(new UnbalancedVouchers(List.of(), 0L));
        given(ledgerPort.findByTradeDate(DAY, null, CHUNK_SIZE))
                .willReturn(List.of(tx("A1", "IMMEDIATE_TRANSFER", 100L), tx("A2", "INTERBANK_TRANSFER", 50L)));
        given(ledgerPort.findByTradeDate(DAY, "A2", CHUNK_SIZE))
                .willReturn(List.of(tx("A3", "PRODUCT_SUBSCRIPTION", 300L)));
        given(glPort.sumVoucherTotals(DAY, Set.of(GlTxType.TRANSFER), Set.of("A1")))
                .willReturn(List.of(new VoucherTotals(GlTxType.TRANSFER, "A1", 100L, 100L)));
        given(glPort.sumVoucherTotals(DAY, Set.of(GlTxType.PRODUCT_SUBSCRIPTION), Set.of("A3")))
                .willReturn(List.of());

        GlIntegrityReport report = service.check(DAY);

        assertThat(report.checkedTransactionCount()).isEqualTo(2);
        assertThat(report.excludedTransactionCounts()).isEqualTo(Map.of("INTERBANK_TRANSFER", 1L));
        assertThat(report.ledgerVoucherMismatches())
                .extracting(LedgerVoucherMismatch::transactionNumber, LedgerVoucherMismatch::kind)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("A3", Kind.MISSING_VOUCHER));
        assertThat(report.ledgerVoucherMismatchCount()).isEqualTo(1);
        assertThat(report.clean()).isFalse();
    }

    @Test
    @DisplayName("전표유형이 다르면 같은 참조 키라도 그 거래의 전표로 보지 않는다")
    void matchesVoucherByTypeAndKey() {
        givenBusinessDateAndVoucherCount(DAY.plusDays(1), 0L);
        given(glPort.findUnbalancedVouchers(DAY, GlIntegrityCheckService.LIST_LIMIT))
                .willReturn(new UnbalancedVouchers(List.of(), 0L));
        given(ledgerPort.findByTradeDate(DAY, null, CHUNK_SIZE)).willReturn(List.of(tx("A1", "REVERSAL", 100L)));
        given(glPort.sumVoucherTotals(DAY, Set.of(GlTxType.REVERSAL), Set.of("A1")))
                .willReturn(List.of(new VoucherTotals(GlTxType.TRANSFER, "A1", 100L, 100L)));

        assertThat(service.check(DAY).ledgerVoucherMismatches())
                .extracting(LedgerVoucherMismatch::kind)
                .containsExactly(Kind.MISSING_VOUCHER);
    }

    @Test
    @DisplayName("불일치 목록은 100건까지만 담고 건수는 전체를 센다")
    void capsListButCountsAll() {
        service = new GlIntegrityCheckService(ledgerPort, glPort, businessDateGate, transactionManager, 1_000);
        List<LedgerTransaction> chunk = new ArrayList<>();
        IntStream.range(0, 150).forEach(i -> chunk.add(tx("T%03d".formatted(i), "AUTO_TRANSFER", 10L)));
        givenBusinessDateAndVoucherCount(DAY.plusDays(1), 0L);
        given(glPort.findUnbalancedVouchers(DAY, GlIntegrityCheckService.LIST_LIMIT))
                .willReturn(new UnbalancedVouchers(
                        List.of(new UnbalancedVoucher("20261001-TRF-000001", GlTxType.TRANSFER, "X", 10L, 0L, 1L)),
                        250L));
        given(ledgerPort.findByTradeDate(DAY, null, 1_000)).willReturn(chunk);
        given(glPort.sumVoucherTotals(any(), any(), anyCollection())).willReturn(List.of());

        GlIntegrityReport report = service.check(DAY);

        assertThat(report.ledgerVoucherMismatches()).hasSize(GlIntegrityCheckService.LIST_LIMIT);
        assertThat(report.ledgerVoucherMismatchCount()).isEqualTo(150);
        assertThat(report.unbalancedVoucherCount()).isEqualTo(250);
    }

    @Test
    @DisplayName("원장 거래가 없으면 대사 없이 끝나고, 차대변 불일치도 없으면 clean 이다")
    void cleanWhenNothingWrong() {
        givenBusinessDateAndVoucherCount(DAY.plusDays(1), 0L);
        given(glPort.findUnbalancedVouchers(DAY, GlIntegrityCheckService.LIST_LIMIT))
                .willReturn(new UnbalancedVouchers(List.of(), 0L));
        given(ledgerPort.findByTradeDate(DAY, null, CHUNK_SIZE)).willReturn(List.of());

        GlIntegrityReport report = service.check(DAY);

        assertThat(report.clean()).isTrue();
        assertThat(report.checkedTransactionCount()).isZero();
        verify(glPort, never()).sumVoucherTotals(any(), any(), anyCollection());
    }

    @Test
    @DisplayName("거래일이 없으면 CMN0002")
    void rejectsMissingTradeDate() {
        assertThatThrownBy(() -> service.check(null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING);
    }

    @Test
    @DisplayName("대상 유형 전표 수에서 짝지어진 전표 수를 뺀 만큼 고아 전표로 세고, 마감된 거래일이면 확정값이다")
    void countsOrphanVouchers() {
        givenBusinessDateAndVoucherCount(DAY.plusDays(1), 3L);
        given(glPort.findUnbalancedVouchers(DAY, GlIntegrityCheckService.LIST_LIMIT))
                .willReturn(new UnbalancedVouchers(List.of(), 0L));
        given(ledgerPort.findByTradeDate(DAY, null, CHUNK_SIZE))
                .willReturn(List.of(tx("A1", "IMMEDIATE_TRANSFER", 100L), tx("A2", "IMMEDIATE_TRANSFER", 100L)));
        given(glPort.sumVoucherTotals(DAY, Set.of(GlTxType.TRANSFER), Set.of("A1", "A2")))
                .willReturn(List.of(new VoucherTotals(GlTxType.TRANSFER, "A1", 100L, 100L)));
        given(ledgerPort.findByTradeDate(DAY, "A2", CHUNK_SIZE)).willReturn(List.of());

        GlIntegrityReport report = service.check(DAY);

        // 전표 3건 중 A1 하나만 원장과 짝이다. A2 는 전표 없음(MISSING_VOUCHER)으로 따로 센다.
        assertThat(report.orphanVoucherCount()).isEqualTo(2);
        assertThat(report.orphanVoucherCountFinal()).isTrue();
        assertThat(report.ledgerVoucherMismatchCount()).isEqualTo(1);
        assertThat(report.clean()).isFalse();
    }

    @Test
    @DisplayName("현재 영업일과 같은 거래일은 고아 전표 수가 참고값이고, 점검 중 기표로 짝이 더 많아도 음수가 되지 않는다")
    void orphanCountIsProvisionalOnCurrentBusinessDate() {
        givenBusinessDateAndVoucherCount(DAY, 0L);
        given(glPort.findUnbalancedVouchers(DAY, GlIntegrityCheckService.LIST_LIMIT))
                .willReturn(new UnbalancedVouchers(List.of(), 0L));
        given(ledgerPort.findByTradeDate(DAY, null, CHUNK_SIZE))
                .willReturn(List.of(tx("A1", "IMMEDIATE_TRANSFER", 100L)));
        given(glPort.sumVoucherTotals(DAY, Set.of(GlTxType.TRANSFER), Set.of("A1")))
                .willReturn(List.of(new VoucherTotals(GlTxType.TRANSFER, "A1", 100L, 100L)));

        GlIntegrityReport report = service.check(DAY);

        assertThat(report.orphanVoucherCount()).isZero();
        assertThat(report.orphanVoucherCountFinal()).isFalse();
        assertThat(report.clean()).isTrue();
    }

    private void givenBusinessDateAndVoucherCount(LocalDate businessDate, long voucherCount) {
        given(businessDateGate.acquireSharedAndReadBusinessDate()).willReturn(businessDate);
        given(glPort.countVouchers(eq(DAY), anyCollection())).willReturn(voucherCount);
    }

    private static LedgerTransaction tx(String number, String type, long amount) {
        return new LedgerTransaction(number, type, amount);
    }
}
