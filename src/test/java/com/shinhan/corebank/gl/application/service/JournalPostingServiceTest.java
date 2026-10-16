package com.shinhan.corebank.gl.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.gl.api.GlTxType;
import com.shinhan.corebank.gl.api.JournalDirection;
import com.shinhan.corebank.gl.api.JournalLine;
import com.shinhan.corebank.gl.api.JournalPostingUseCase;
import com.shinhan.corebank.gl.api.JournalRequest;
import com.shinhan.corebank.gl.domain.exception.GlErrorCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("전표 기표(JournalPostingService) 통합 테스트")
class JournalPostingServiceTest extends IntegrationTestSupport {

    // 시드(PH-60b)·다른 GL 테스트와 날짜가 겹치지 않도록 먼 미래 거래일만 쓴다.
    private static final LocalDate TRADE_DATE = LocalDate.of(2099, 2, 3);

    @Autowired
    private JournalPostingUseCase journalPostingUseCase;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM gl_journal_entry WHERE trade_date = ?", TRADE_DATE);
        jdbcTemplate.update("DELETE FROM gl_voucher WHERE trade_date = ?", TRADE_DATE);
        jdbcTemplate.update("DELETE FROM gl_voucher_sequence WHERE trade_date = ?", TRADE_DATE);
    }

    @Test
    @DisplayName("호출자 트랜잭션 안에서 전표 1건과 분개 줄을 참조 키와 함께 기표한다")
    void postsVoucherInCallerTransaction() {
        transactionTemplate.executeWithoutResult(
                status -> journalPostingUseCase.post(transfer("20990203WB0000000001")));

        Map<String, Object> voucher = jdbcTemplate.queryForMap(
                "SELECT voucher_no, tx_type, reference_key FROM gl_voucher WHERE trade_date = ?", TRADE_DATE);
        assertThat(voucher.get("voucher_no")).isEqualTo("20990203-TRF-000001");
        assertThat(voucher.get("tx_type")).isEqualTo("TRANSFER");
        assertThat(voucher.get("reference_key")).isEqualTo("20990203WB0000000001");
        assertThat(jdbcTemplate.queryForList(
                        "SELECT account_code, dr_cr, amount FROM gl_journal_entry WHERE voucher_no = ? ORDER BY line_no",
                        "20990203-TRF-000001"))
                .extracting(row -> row.get("account_code"), row -> row.get("dr_cr"), row -> row.get("amount"))
                .containsExactly(tuple("20100", "DEBIT", 10_000L), tuple("20100", "CREDIT", 10_000L));
    }

    @Test
    @DisplayName("트랜잭션 없이 부르면 즉시 실패하고 아무것도 남지 않는다 — MANDATORY")
    void rejectsCallWithoutTransaction() {
        assertThatThrownBy(() -> journalPostingUseCase.post(transfer("20990203WB0000000002")))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(voucherCount()).isZero();
    }

    @Test
    @DisplayName("같은 거래를 두 번 기표하면 GLA9008 이고 호출자 트랜잭션이 롤백된다")
    void rejectsDuplicateReferenceKeyAndRollsBackCaller() {
        String referenceKey = "20990203WB0000000003";
        transactionTemplate.executeWithoutResult(status -> journalPostingUseCase.post(transfer(referenceKey)));

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
                    jdbcTemplate.update(
                            "INSERT INTO gl_voucher (voucher_no, trade_date, tx_type, reference_key)"
                                    + " VALUES ('20990203-TRF-999999', ?, 'TRANSFER', 'CALLER-WRITE')",
                            TRADE_DATE);
                    journalPostingUseCase.post(transfer(referenceKey));
                }))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(GlErrorCode.DUPLICATE_REFERENCE_KEY);

        // 호출자가 같은 트랜잭션에서 쓴 행도 함께 롤백된다 — 원장이 여기에 해당한다.
        assertThat(voucherCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("차대변이 맞지 않으면 GLA9001 이고 전표가 남지 않는다")
    void rejectsUnbalancedRequest() {
        JournalRequest unbalanced = new JournalRequest(
                GlTxType.TRANSFER,
                "20990203WB0000000004",
                TRADE_DATE,
                List.of(
                        new JournalLine("20100", JournalDirection.DEBIT, 10_000L),
                        new JournalLine("20100", JournalDirection.CREDIT, 9_999L)));

        assertThatThrownBy(() ->
                        transactionTemplate.executeWithoutResult(status -> journalPostingUseCase.post(unbalanced)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(GlErrorCode.UNBALANCED_VOUCHER);

        assertThat(voucherCount()).isZero();
    }

    private static JournalRequest transfer(String referenceKey) {
        return new JournalRequest(
                GlTxType.TRANSFER,
                referenceKey,
                TRADE_DATE,
                List.of(
                        new JournalLine("20100", JournalDirection.DEBIT, 10_000L),
                        new JournalLine("20100", JournalDirection.CREDIT, 10_000L)));
    }

    private int voucherCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM gl_voucher WHERE trade_date = ?", Integer.class, TRADE_DATE);
    }
}
