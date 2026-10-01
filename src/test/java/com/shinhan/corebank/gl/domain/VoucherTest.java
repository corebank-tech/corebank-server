package com.shinhan.corebank.gl.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.gl.domain.exception.GlErrorCode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Voucher 도메인 단위 테스트")
class VoucherTest {

    private static final LocalDate TRADE_DATE = LocalDate.of(2026, 9, 22);
    private static final VoucherNumber TRANSFER_NO = new VoucherNumber(TRADE_DATE, GlTxType.TRANSFER, 123);
    private static final VoucherNumber OPENING_NO = new VoucherNumber(TRADE_DATE, GlTxType.OPENING, 1);

    @Nested
    @DisplayName("전표번호")
    class VoucherNumberTest {

        @Test
        @DisplayName("거래일-유형3자-일련6자리 19자로 만든다")
        void formatsNineteenCharacters() {
            assertThat(TRANSFER_NO.value()).isEqualTo("20260922-TRF-000123").hasSize(19);
        }

        @Test
        @DisplayName("일련번호가 1보다 작으면 호출 코드 오류라 IllegalArgumentException")
        void rejectsNonPositiveSequenceAsProgrammingError() {
            assertThatThrownBy(() -> new VoucherNumber(TRADE_DATE, GlTxType.TRANSFER, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("sequence");
        }

        @Test
        @DisplayName("일련번호가 999999보다 크면 GLA9005")
        void rejectsSequenceAboveMax() {
            assertThatThrownBy(() -> new VoucherNumber(TRADE_DATE, GlTxType.TRANSFER, 1_000_000))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(GlErrorCode.VOUCHER_SEQUENCE_EXHAUSTED);
        }
    }

    @Nested
    @DisplayName("차대변 검증")
    class BalanceTest {

        @Test
        @DisplayName("차변 합계와 대변 합계가 같으면 만들어지고 거래일·유형은 전표번호에서 온다")
        void createsBalancedVoucher() {
            Voucher voucher = Voucher.create(
                    TRANSFER_NO,
                    null,
                    List.of(JournalEntry.debit("20100", 10_000L), JournalEntry.credit("20100", 10_000L)));

            assertThat(voucher.getTradeDate()).isEqualTo(TRADE_DATE);
            assertThat(voucher.getTxType()).isEqualTo(GlTxType.TRANSFER);
            assertThat(voucher.getEntries()).hasSize(2);
        }

        @Test
        @DisplayName("한쪽에 여러 줄이 있어도 합계가 같으면 만들어진다")
        void createsBalancedVoucherWithMultipleLinesOnOneSide() {
            Voucher voucher = Voucher.create(
                    TRANSFER_NO,
                    null,
                    List.of(
                            JournalEntry.debit("10100", 10_000L),
                            JournalEntry.credit("20100", 7_000L),
                            JournalEntry.credit("30100", 3_000L)));

            assertThat(voucher.getEntries()).hasSize(3);
        }

        @Test
        @DisplayName("차변 합계와 대변 합계가 다르면 GLA9001")
        void rejectsUnbalancedVoucher() {
            List<JournalEntry> entries =
                    List.of(JournalEntry.debit("20100", 10_000L), JournalEntry.credit("20100", 9_999L));

            assertThatThrownBy(() -> Voucher.create(TRANSFER_NO, null, entries))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(GlErrorCode.UNBALANCED_VOUCHER);
        }

        @Test
        @DisplayName("한쪽 방향 줄만 있으면 GLA9001 — 편측기표")
        void rejectsOneSidedVoucher() {
            List<JournalEntry> entries =
                    List.of(JournalEntry.debit("20100", 10_000L), JournalEntry.debit("10100", 10_000L));

            assertThatThrownBy(() -> Voucher.create(TRANSFER_NO, null, entries))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(GlErrorCode.UNBALANCED_VOUCHER);
        }

        @Test
        @DisplayName("분개가 1줄이면 GLA9002")
        void rejectsSingleLineVoucher() {
            List<JournalEntry> entries = List.of(JournalEntry.debit("20100", 10_000L));

            assertThatThrownBy(() -> Voucher.create(TRANSFER_NO, null, entries))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(GlErrorCode.TOO_FEW_JOURNAL_ENTRIES);
        }

        @Test
        @DisplayName("분개 금액이 0 이하면 GLA9003")
        void rejectsNonPositiveAmount() {
            assertThatThrownBy(() -> JournalEntry.debit("20100", 0L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(GlErrorCode.NON_POSITIVE_JOURNAL_AMOUNT);
            assertThatThrownBy(() -> JournalEntry.credit("20100", -1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(GlErrorCode.NON_POSITIVE_JOURNAL_AMOUNT);
        }
    }

    @Nested
    @DisplayName("개시 잔액 전표")
    class OpeningTest {

        @Test
        @DisplayName("차 현금성 / 대 예수금 + 대 개시잔액(차액) 3줄을 만든다")
        void createsThreeLines() {
            Voucher voucher = Voucher.opening(OPENING_NO, 1_000_000L, 700_000L);

            assertThat(voucher.getEntries())
                    .extracting(JournalEntry::accountCode, JournalEntry::drCr, JournalEntry::amount)
                    .containsExactly(
                            tuple("10100", JournalDirection.DEBIT, 1_000_000L),
                            tuple("20100", JournalDirection.CREDIT, 700_000L),
                            tuple("30100", JournalDirection.CREDIT, 300_000L));
        }

        @Test
        @DisplayName("고객 잔액 합계가 0이면 예수금 줄을 만들지 않는다")
        void omitsDepositLineWhenZero() {
            Voucher voucher = Voucher.opening(OPENING_NO, 1_000_000L, 0L);

            assertThat(voucher.getEntries())
                    .extracting(JournalEntry::accountCode)
                    .containsExactly("10100", "30100");
        }

        @Test
        @DisplayName("현금성 총액과 고객 잔액 합계가 같으면 개시잔액 줄을 만들지 않는다")
        void omitsEquityLineWhenZero() {
            Voucher voucher = Voucher.opening(OPENING_NO, 700_000L, 700_000L);

            assertThat(voucher.getEntries())
                    .extracting(JournalEntry::accountCode)
                    .containsExactly("10100", "20100");
        }

        @Test
        @DisplayName("현금성 총액이 고객 잔액 합계보다 작으면 GLA9004")
        void rejectsCashBelowDeposits() {
            assertThatThrownBy(() -> Voucher.opening(OPENING_NO, 699_999L, 700_000L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(GlErrorCode.OPENING_CASH_BELOW_DEPOSITS);
        }

        @Test
        @DisplayName("전표번호 유형이 OPENING 이 아니면 GLA9006")
        void rejectsNonOpeningNumber() {
            assertThatThrownBy(() -> Voucher.opening(TRANSFER_NO, 1_000_000L, 700_000L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(GlErrorCode.VOUCHER_TYPE_MISMATCH);
        }

        @Test
        @DisplayName("둘 다 0이면 분개가 없어 GLA9002")
        void rejectsEmptyOpening() {
            assertThatThrownBy(() -> Voucher.opening(OPENING_NO, 0L, 0L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(GlErrorCode.TOO_FEW_JOURNAL_ENTRIES);
        }
    }
}
