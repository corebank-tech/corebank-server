-- PH-30 정합성 판정 (#384)
--
-- 부하·정합성 시나리오를 돌린 뒤 실행한다. 판정 불변식 3개가 각각 한 쿼리다.
-- 검증 로직은 기존 동시성 테스트에서 가져왔다 —
--   TransferExecutionServiceConcurrencyTest (원장 짝·잔액)
--   TransferLimitReserveConcurrencyTest     (한도 적립)
--
-- 결함 판정 경계: 데드락 롤백 후 실패 응답이 나간 건은 정상 거절이다.
-- 성공 응답이 나갔는데 원장이 안 맞는 것만 결함으로 센다. 그래서 전부 status='SUCCESS' 로 좁힌다.
--
-- 아래 두 값을 측정 구간에 맞춰 바꾼다.
SET @from_at = '2026-10-01 00:00:00';
SET @to_at   = '2026-10-02 00:00:00';

-- ---------------------------------------------------------------------------
-- 1. 원장 차변/대변 짝 — 성공 이체 1건당 출금 1행 + 입금 1행이어야 한다
--    기대: 0
-- ---------------------------------------------------------------------------
SELECT COUNT(*) AS broken_ledger_pairs
FROM (
    SELECT t.transfer_id
    FROM transfer t
    -- LEFT JOIN 이어야 한다. INNER JOIN 이면 원장이 0행인 성공 이체가 조인에서 사라져
    -- HAVING 까지 가지 못하고, 가장 심각한 짝 깨짐이 0 으로 나온다.
    LEFT JOIN ledger_entry le ON le.transfer_id = t.transfer_id
    WHERE t.status = 'SUCCESS'
      AND t.transferred_at >= @from_at
      AND t.transferred_at <  @to_at
    GROUP BY t.transfer_id
    HAVING COUNT(le.transfer_id) <> 2
        OR SUM(le.direction = 'WITHDRAWAL') <> 1
        OR SUM(le.direction = 'DEPOSIT') <> 1
) broken;

-- ---------------------------------------------------------------------------
-- 2. 잔액 오차 — 계좌 잔액이 그 계좌의 원장 합계와 같아야 한다
--    입금은 더하고 출금은 뺀다. 측정에 쓴 계좌만 본다.
--    기대: 0
-- ---------------------------------------------------------------------------
SELECT COUNT(*) AS balance_mismatches
FROM (
    SELECT a.account_id
    FROM account a
    JOIN ledger_entry le ON le.account_id = a.account_id
    WHERE a.account_id IN (
        SELECT DISTINCT le2.account_id
        FROM ledger_entry le2
        JOIN transfer t2 ON t2.transfer_id = le2.transfer_id
        WHERE t2.status = 'SUCCESS'
          AND t2.transferred_at >= @from_at
          AND t2.transferred_at <  @to_at
    )
    GROUP BY a.account_id, a.balance
    HAVING a.balance <> SUM(
        CASE WHEN le.direction = 'DEPOSIT' THEN le.amount ELSE -le.amount END
    )
) mismatches;

-- ---------------------------------------------------------------------------
-- 3. 한도 적립 합 = 실제 이체액
--    같은 날 SUCCESS 로 확정된 출금 합계가 transfer_limit_daily_usage 와 맞아야 한다.
--    출금 고객 기준으로 집계한다 — 한도는 돈을 보내는 쪽에만 쌓인다.
--    기대: 0
--
--    성공 이체 쪽에서 시작해 usage 를 LEFT JOIN 한다. usage 에서 시작하면 적립 행이 아예
--    없는 고객, 즉 「적립 누락」이 결과에서 빠져 0 으로 나온다.
--    used_amount 는 하루 누계라 비교는 반드시 하루 전체로 한다 — 측정 구간이 몇 시간이어도
--    그 구간 합계와 비교하면 전부 불일치로 잡힌다. 구간이 걸친 날은 모두 검사한다.
-- ---------------------------------------------------------------------------
SELECT COUNT(*) AS limit_usage_mismatches
FROM (
    SELECT a.customer_id,
           DATE(t.transferred_at) AS usage_date,
           SUM(t.amount)          AS actual_amount
    FROM transfer t
    JOIN account a ON a.account_id = t.withdrawal_account_id
    WHERE t.status = 'SUCCESS'
      AND DATE(t.transferred_at)
          BETWEEN DATE(@from_at) AND DATE(DATE_SUB(@to_at, INTERVAL 1 SECOND))
    GROUP BY a.customer_id, DATE(t.transferred_at)
) daily
LEFT JOIN transfer_limit_daily_usage u
       ON u.customer_id = daily.customer_id
      AND u.usage_date  = daily.usage_date
WHERE COALESCE(u.used_amount, 0) <> daily.actual_amount;

-- ---------------------------------------------------------------------------
-- 참고. 측정 구간 요약 — 판정은 아니고 리포트에 적을 건수다
-- ---------------------------------------------------------------------------
SELECT
    COUNT(*)                                         AS total_transfers,
    SUM(status = 'SUCCESS')                          AS success_count,
    SUM(status = 'ERROR')                            AS error_count,
    SUM(CASE WHEN status = 'SUCCESS' THEN amount END) AS success_amount
FROM transfer
WHERE transferred_at >= @from_at
  AND transferred_at <  @to_at;
