package com.shinhan.corebank.paymentstop.support;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 지급정지 통합 테스트용 원장 데이터. 테스트마다 고객 하나를 만들고 그 고객 밑에 계좌, 이체, 원장을 JDBC로 넣는다.
 *
 * <p>대역: 계좌번호 086, 거래번호 20991231WB, 원장 ID 894xxxxxxx. 전체 실행에서는 고객 ID 가 커지므로 나머지로 대역을 만든다.
 */
public class PaymentStopTestData {

    public static final long DISPUTED_AMOUNT = 300_000L;

    private static final String PASSWORD_HASH = "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq";

    private final JdbcTemplate jdbcTemplate;
    private final long customerId;
    private final LocalDateTime disputedAt;
    private final List<Long> accountIds = new ArrayList<>();
    private int sequence;

    public PaymentStopTestData(JdbcTemplate jdbcTemplate, long customerId, LocalDateTime disputedAt) {
        this.jdbcTemplate = jdbcTemplate;
        this.customerId = customerId;
        this.disputedAt = disputedAt;
    }

    public LocalDateTime disputedAt() {
        return disputedAt;
    }

    /** 지급정지 계좌, 송금인 계좌, 다툼 입금 1건. withRelationship 이면 다툼 이전 관계 거래 3회(40일, 20일, 5일 전)를 둔다. */
    public Scenario scenario(boolean withRelationship) {
        long target = insertAccount(1_000_000L);
        long sender = insertAccount(1_000_000L);
        if (withRelationship) {
            transfer(sender, target, 100_000L, disputedAt.minusDays(40));
            transfer(target, sender, 100_000L, disputedAt.minusDays(20));
            transfer(sender, target, 100_000L, disputedAt.minusDays(5));
        }
        String disputed = transfer(sender, target, DISPUTED_AMOUNT, disputedAt);
        return new Scenario(target, sender, disputed);
    }

    public long insertAccount(long balance) {
        String accountNumber = String.format("086%09d", band() + sequence++);
        jdbcTemplate.update(
                """
                INSERT INTO account (account_number, customer_id, account_type, balance, status,
                                     password_hash, opened_date, created_at, updated_at)
                VALUES (?, ?, 'DEMAND_DEPOSIT', ?, 'ACTIVE', ?, '2026-01-01', NOW(6), NOW(6))
                """,
                accountNumber,
                customerId,
                balance,
                PASSWORD_HASH);
        long id = jdbcTemplate.queryForObject(
                "SELECT account_id FROM account WHERE account_number = ?", Long.class, accountNumber);
        accountIds.add(id);
        return id;
    }

    /** 당행 이체 1건과 원장 2행(출금, 입금). 거래번호를 돌려준다. */
    public String transfer(long from, long to, long amount, LocalDateTime at) {
        int serial = sequence++;
        String transactionNumber = String.format("20991231WB%010d", band() + serial);
        String toNumber = jdbcTemplate.queryForObject(
                "SELECT account_number FROM account WHERE account_id = ?", String.class, to);
        jdbcTemplate.update(
                """
                INSERT INTO transfer (transaction_number, withdrawal_account_id, deposit_account_id, deposit_account_number,
                                      payee_name, amount, fee, transfer_type, channel, status, transferred_at, created_at)
                VALUES (?, ?, ?, ?, '홍길동', ?, 0, 'IMMEDIATE', 'WB', 'SUCCESS', ?, NOW(6))
                """,
                transactionNumber,
                from,
                to,
                toNumber,
                amount,
                Timestamp.valueOf(at));
        long transferId = jdbcTemplate.queryForObject(
                "SELECT transfer_id FROM transfer WHERE transaction_number = ?", Long.class, transactionNumber);
        insertLedger(from, transferId, transactionNumber, "WITHDRAWAL", amount, at, serial * 2);
        insertLedger(to, transferId, transactionNumber, "DEPOSIT", amount, at, serial * 2 + 1);
        return transactionNumber;
    }

    /** 이 고객 밑에 만든 사건, 지급정지, 원장, 계좌와 이 고객의 감사, 멱등키 행을 지운다. 고객 행은 호출자가 지운다. */
    public void cleanup() {
        String ids = accountIds.isEmpty()
                ? "-1"
                : String.join(",", accountIds.stream().map(String::valueOf).toList());
        String cases = "SELECT case_id FROM payment_stop_case WHERE account_id IN (" + ids + ")";
        String objections = "SELECT objection_id FROM objection WHERE case_id IN (" + cases + ")";
        String runs = "SELECT run_id FROM agent_run WHERE objection_id IN (" + objections + ")";
        String documents = "SELECT document_id FROM objection_document WHERE objection_id IN (" + objections + ")";
        jdbcTemplate.update("DELETE FROM objection_document_ocr WHERE document_id IN (" + documents + ")");
        jdbcTemplate.update("DELETE FROM objection_document WHERE objection_id IN (" + objections + ")");
        jdbcTemplate.update("DELETE FROM objection_notice WHERE objection_id IN (" + objections + ")");
        jdbcTemplate.update("DELETE FROM objection_external_check WHERE objection_id IN (" + objections + ")");
        jdbcTemplate.update("DELETE FROM objection_review_session WHERE objection_id IN (" + objections + ")");
        jdbcTemplate.update("DELETE FROM objection_supplement_request WHERE objection_id IN (" + objections + ")");
        jdbcTemplate.update("DELETE FROM agent_run_step WHERE run_id IN (" + runs + ")");
        jdbcTemplate.update("DELETE FROM review_draft WHERE objection_id IN (" + objections + ")");
        jdbcTemplate.update("DELETE FROM agent_run WHERE objection_id IN (" + objections + ")");
        jdbcTemplate.update("DELETE FROM gate_evaluation WHERE objection_id IN (" + objections + ")");
        jdbcTemplate.update("DELETE FROM objection_decision WHERE objection_id IN (" + objections + ")");
        jdbcTemplate.update("DELETE FROM objection WHERE case_id IN (" + cases + ")");
        jdbcTemplate.update("DELETE FROM case_disputed_transfer WHERE case_id IN (" + cases + ")");
        jdbcTemplate.update("DELETE FROM payment_stop_case WHERE account_id IN (" + ids + ")");
        jdbcTemplate.update("DELETE FROM account_payment_stop WHERE account_id IN (" + ids + ")");
        jdbcTemplate.update("DELETE FROM account_status_history WHERE account_id IN (" + ids + ")");
        jdbcTemplate.update("DELETE FROM audit_log WHERE customer_id = ?", customerId);
        jdbcTemplate.update("DELETE FROM idempotency_key WHERE customer_id = ?", customerId);
        // MockMvc 인증 요청이 남긴 세션. 다른 테스트의 세션 테이블 잠금 간격에 끼어들지 않게 지운다(속성은 FK CASCADE)
        jdbcTemplate.update("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?", String.valueOf(customerId));
        jdbcTemplate.update("DELETE FROM ledger_entry WHERE account_id IN (" + ids + ")");
        jdbcTemplate.update("DELETE FROM transfer WHERE withdrawal_account_id IN (" + ids + ")");
        jdbcTemplate.update("DELETE FROM account WHERE customer_id = ?", customerId);
    }

    /** 승인자처럼 계좌 없이 요청만 한 다른 관리자의 감사, 멱등키, 세션 행을 지운다. 고객 행은 호출자가 지운다. */
    public void cleanupActor(long actorId) {
        jdbcTemplate.update("DELETE FROM audit_log WHERE customer_id = ?", actorId);
        jdbcTemplate.update("DELETE FROM idempotency_key WHERE customer_id = ?", actorId);
        jdbcTemplate.update("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?", String.valueOf(actorId));
    }

    public String objectionStatus(long objectionId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM objection WHERE objection_id = ?", String.class, objectionId);
    }

    /** 통지 기록을 "대상:종류" 로 기록 순서대로 */
    public List<String> notices(long objectionId) {
        return jdbcTemplate.queryForList(
                "SELECT CONCAT(target, ':', kind) FROM objection_notice WHERE objection_id = ? ORDER BY notice_id",
                String.class,
                objectionId);
    }

    public long countAudit(long actorId, String eventType) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE customer_id = ? AND event_type = ?",
                Long.class,
                actorId,
                eventType);
    }

    public String status(long accountId) {
        return jdbcTemplate.queryForObject("SELECT status FROM account WHERE account_id = ?", String.class, accountId);
    }

    public long holdAmount(long accountId) {
        return jdbcTemplate.queryForObject(
                "SELECT hold_amount FROM account WHERE account_id = ?", Long.class, accountId);
    }

    public long auditCount(String eventType) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE customer_id = ? AND event_type = ?",
                Long.class,
                customerId,
                eventType);
    }

    private void insertLedger(
            long accountId,
            long transferId,
            String transactionNumber,
            String direction,
            long amount,
            LocalDateTime at,
            int slot) {
        jdbcTemplate.update(
                """
                INSERT INTO ledger_entry (ledger_entry_id, account_id, transfer_id, transaction_number, direction, amount,
                                          balance_after, transaction_type, transaction_content, channel, reversed,
                                          reversal_id, occurred_at, trade_date)
                VALUES (?, ?, ?, ?, ?, ?, 0, 'IMMEDIATE_TRANSFER', 'PA04', 'WB', FALSE, NULL, ?, ?)
                """,
                894_000_000_000L + (customerId % 1_000_000L) * 10_000L + slot,
                accountId,
                transferId,
                transactionNumber,
                direction,
                amount,
                Timestamp.valueOf(at),
                at.toLocalDate());
    }

    private long band() {
        return (customerId % 1_000_000L) * 1000L;
    }

    public record Scenario(long target, long sender, String disputed) {}
}
