package com.shinhan.corebank.transfer.application.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.SoftAssertions;
import org.springframework.jdbc.core.JdbcTemplate;

// PH-36 자금 정합성(#711). 시작할 때 잔액을 찍어 두고, 그 뒤에 생긴 이 계좌의 타행 거래만 모아 4항목을 본다.
final class FundIntegrityCheck {

    private final JdbcTemplate jdbcTemplate;
    private final long accountId;
    private final long startBalance;
    private final long transferIdFloor;

    private FundIntegrityCheck(JdbcTemplate jdbcTemplate, long accountId, long startBalance, long transferIdFloor) {
        this.jdbcTemplate = jdbcTemplate;
        this.accountId = accountId;
        this.startBalance = startBalance;
        this.transferIdFloor = transferIdFloor;
    }

    static FundIntegrityCheck begin(JdbcTemplate jdbcTemplate, long accountId) {
        long balance =
                jdbcTemplate.queryForObject("SELECT balance FROM account WHERE account_id = ?", Long.class, accountId);
        long floor = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(transfer_id), 0) FROM transfer", Long.class);
        return new FundIntegrityCheck(jdbcTemplate, accountId, balance, floor);
    }

    void assertHolds() {
        List<Map<String, Object>> transfers = jdbcTemplate.queryForList(
                """
                SELECT transaction_number, status, amount FROM transfer
                WHERE withdrawal_account_id = ? AND payee_bank_code IS NOT NULL AND transfer_id > ?
                """,
                accountId,
                transferIdFloor);
        long balance =
                jdbcTemplate.queryForObject("SELECT balance FROM account WHERE account_id = ?", Long.class, accountId);
        long succeeded = sumAmount(transfers, "SUCCESS");
        long timedOut = sumAmount(transfers, "TIMEOUT");
        // 10200은 송신 때 대변으로 쌓이므로 차변−대변 순잔액의 부호를 뒤집어야 미결제 금액이다.
        long unsettled = -transfers.stream()
                .mapToLong(row -> unsettledNet((String) row.get("transaction_number")))
                .sum();

        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(balance)
                .as("① 계좌 잔액 = 마지막 원장 balance_after")
                .isEqualTo(lastBalanceAfter().orElse(startBalance));
        softly.assertThat(startBalance - succeeded)
                .as("② 시작 잔액 − 확정 성공 = 현재 잔액 + 미결제")
                .isEqualTo(balance + unsettled);
        softly.assertThat(unsettled).as("③ 미결제(10200) = TIMEOUT 금액 합계").isEqualTo(timedOut);
        for (Map<String, Object> row : transfers) {
            String transactionNumber = (String) row.get("transaction_number");
            long withdrawals = ledgerRows(transactionNumber, "WITHDRAWAL");
            boolean refundable = "ERROR".equals(row.get("status")) && withdrawals == 1;
            softly.assertThat(withdrawals)
                    .as("출금은 거래번호당 최대 1행 - %s", transactionNumber)
                    .isLessThanOrEqualTo(1);
            softly.assertThat(ledgerRows(transactionNumber, "DEPOSIT"))
                    .as("④ 되돌림은 송신 뒤 실패에만 정확히 1행 - %s %s", transactionNumber, row.get("status"))
                    .isEqualTo(refundable ? 1 : 0);
        }
        softly.assertAll();
    }

    private static long sumAmount(List<Map<String, Object>> transfers, String status) {
        return transfers.stream()
                .filter(row -> status.equals(row.get("status")))
                .mapToLong(row -> ((Number) row.get("amount")).longValue())
                .sum();
    }

    // 마지막 잔액은 occurred_at, 그다음 ledger_entry_id 순으로 고른다(schema_reference.md ledger_entry, #545).
    private Optional<Long> lastBalanceAfter() {
        return jdbcTemplate
                .queryForList(
                        """
                        SELECT balance_after FROM ledger_entry WHERE account_id = ?
                        ORDER BY occurred_at DESC, ledger_entry_id DESC LIMIT 1
                        """,
                        Long.class,
                        accountId)
                .stream()
                .findFirst();
    }

    private long unsettledNet(String transactionNumber) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(SUM(CASE WHEN e.dr_cr = 'DEBIT' THEN e.amount ELSE -e.amount END), 0)
                FROM gl_journal_entry e JOIN gl_voucher v ON v.voucher_no = e.voucher_no
                WHERE v.reference_key = ? AND e.account_code = '10200'
                """,
                Long.class,
                transactionNumber);
    }

    private long ledgerRows(String transactionNumber, String direction) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger_entry WHERE transaction_number = ? AND direction = ?",
                Long.class,
                transactionNumber,
                direction);
    }
}
