package com.shinhan.corebank.gl.adapter.out.persistence;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GlJournalEntryJpaRepository extends JpaRepository<GlJournalEntryJpaEntity, Long> {

    // 분개를 계정코드로 먼저 집계하고(최대 계정 수만큼의 행), 그 결과에 계정과목을 붙인다. 분개 행마다 조인하지 않는다.
    //
    // 계정과목을 LEFT JOIN 하는 이유: 평소에는 fk_gl_journal_entry_account 가 미등록 코드를 막는다. 다만 FK 를 끄고
    // 넣는 결함 주입(PH-28b)이나 우회 적재가 있으면 INNER JOIN 은 그 분개를 총계에서 빼고 차대변이 맞는 것처럼
    // 보이게 한다(FE REQ-ADM-024).
    //
    // 인덱스는 PH-28 베이스라인을 잰 뒤 GL-IDX 에서 붙인다. perf/sql/ph28_trial_balance_baseline.sql 이 같은 문장을
    // 쓰고, TrialBalanceBaselineSqlTest 가 둘이 같은지 본다.
    String SUM_BY_ACCOUNT_SQL =
            """
            SELECT s.account_code    AS accountCode,
                   a.account_name    AS accountName,
                   a.account_class   AS accountClass,
                   a.normal_balance  AS normalBalance,
                   s.debit_total     AS debitTotal,
                   s.credit_total    AS creditTotal,
                   s.entry_count     AS entryCount
            FROM (
                SELECT je.account_code,
                       SUM(CASE WHEN je.dr_cr = 'DEBIT'  THEN je.amount ELSE 0 END) AS debit_total,
                       SUM(CASE WHEN je.dr_cr = 'CREDIT' THEN je.amount ELSE 0 END) AS credit_total,
                       COUNT(*) AS entry_count
                FROM gl_journal_entry je
                WHERE je.trade_date BETWEEN :fromDate AND :toDate
                GROUP BY je.account_code
            ) s
            LEFT JOIN gl_account a ON a.account_code = s.account_code
            ORDER BY s.account_code
            """;

    @Query(value = SUM_BY_ACCOUNT_SQL, nativeQuery = true)
    List<TrialBalanceRowProjection> sumByAccount(
            @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);
}
