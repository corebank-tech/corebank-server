package com.shinhan.corebank.gl.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.gl.domain.GlTxType;
import com.shinhan.corebank.gl.domain.VoucherNumber;
import com.shinhan.corebank.gl.domain.exception.GlErrorCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@DisplayName("전표번호 채번기(GlVoucherSequenceAdapter) 통합 테스트")
class GlVoucherSequenceAdapterTest extends IntegrationTestSupport {

    // 시드(PH-60b)와 날짜가 겹치지 않도록 먼 미래 거래일만 쓴다.
    private static final LocalDate TRADE_DATE = LocalDate.of(2099, 1, 5);

    @Autowired
    private GlVoucherSequenceAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM gl_voucher_sequence WHERE trade_date = ?", TRADE_DATE);
        jdbcTemplate.update("DELETE FROM gl_voucher WHERE trade_date = ?", TRADE_DATE);
    }

    @Test
    @DisplayName("그날 첫 채번은 000001 이고 이어서 1씩 늘어난다")
    void startsFromOneAndIncrements() {
        VoucherNumber first = adapter.nextVoucherNumber(TRADE_DATE, GlTxType.TRANSFER);
        VoucherNumber second = adapter.nextVoucherNumber(TRADE_DATE, GlTxType.TRANSFER);

        assertThat(first.value()).isEqualTo("20990105-TRF-000001");
        assertThat(second.value()).isEqualTo("20990105-TRF-000002");
    }

    @Test
    @DisplayName("유형마다 따로 센다")
    void countsPerType() {
        adapter.nextVoucherNumber(TRADE_DATE, GlTxType.TRANSFER);
        adapter.nextVoucherNumber(TRADE_DATE, GlTxType.TRANSFER);

        VoucherNumber subscription = adapter.nextVoucherNumber(TRADE_DATE, GlTxType.PRODUCT_SUBSCRIPTION);

        assertThat(subscription.value()).isEqualTo("20990105-SUB-000001");
    }

    @Test
    @DisplayName("카운터가 없는 날에 시드 전표가 이미 있으면 가장 큰 번호 다음부터 센다")
    void continuesAfterSeededVouchers() {
        insertSeedVoucher("20990105-TRF-000041");
        insertSeedVoucher("20990105-TRF-000042");
        insertSeedVoucher("20990105-SUB-000900");

        VoucherNumber next = adapter.nextVoucherNumber(TRADE_DATE, GlTxType.TRANSFER);

        assertThat(next.value()).isEqualTo("20990105-TRF-000043");
    }

    @Test
    @DisplayName("일련번호가 999999에 이르면 GLA9005")
    void rejectsWhenExhausted() {
        jdbcTemplate.update(
                "INSERT INTO gl_voucher_sequence (trade_date, tx_type, last_seq, updated_at) VALUES (?, ?, ?, NOW(6))",
                TRADE_DATE,
                GlTxType.TRANSFER.name(),
                VoucherNumber.MAX_SEQUENCE);

        assertThatThrownBy(() -> adapter.nextVoucherNumber(TRADE_DATE, GlTxType.TRANSFER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(GlErrorCode.VOUCHER_SEQUENCE_EXHAUSTED);
    }

    @Test
    @DisplayName("동시에 채번해도 번호가 겹치지 않는다 — 첫 행 INSERT 경합 포함")
    void issuesDistinctNumbersConcurrently() throws Exception {
        int threads = 16;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return adapter.nextVoucherNumber(TRADE_DATE, GlTxType.TRANSFER)
                            .value();
                }));
            }
            start.countDown();

            List<String> issued = new ArrayList<>();
            for (Future<String> future : futures) {
                issued.add(future.get(30, TimeUnit.SECONDS));
            }
            assertThat(issued).doesNotHaveDuplicates().hasSize(threads);
            assertThat(issued).contains("20990105-TRF-000001", "20990105-TRF-000016");
        } finally {
            executor.shutdownNow();
        }
    }

    private void insertSeedVoucher(String voucherNo) {
        GlTxType txType = voucherNo.contains("-SUB-") ? GlTxType.PRODUCT_SUBSCRIPTION : GlTxType.TRANSFER;
        jdbcTemplate.update(
                "INSERT INTO gl_voucher (voucher_no, trade_date, tx_type, created_at) VALUES (?, ?, ?, NOW(6))",
                voucherNo,
                TRADE_DATE,
                txType.name());
    }
}
