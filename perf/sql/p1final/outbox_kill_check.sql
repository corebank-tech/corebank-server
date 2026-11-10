-- P1-FINAL 알림 유실 · 중복 측정(kill -9). 운영 RDS 에서 읽기만 한다.
-- 실행: mysql ... -e "SET @baseline_transfer_id = <기준선 transfer_id>; SOURCE outbox_kill_check.sql"
-- 단계별로 필요한 블록만 골라 실행해도 된다.

-- [A] 기준선 — 주입 전에 한 번. 출력한 max_transfer_id 를 @baseline_transfer_id 로 쓴다
SELECT (SELECT MAX(transfer_id) FROM transfer) AS max_transfer_id,
       (SELECT COUNT(*) FROM EVENT_PUBLICATION) AS remaining_publications,
       NOW(6) AS checked_at;

-- [B] 출금 고객 1~200번 요구불 잔액 — 최소값이 100만 원을 넉넉히 넘어야 한다
SELECT MIN(balance) AS min_balance, MAX(balance) AS max_balance, COUNT(*) AS accounts
FROM account
WHERE account_id BETWEEN 60000001 AND 60000001 + 199 * 3
  AND MOD(account_id - 60000001, 3) = 0;

-- [C] 남은 발행 기록 상태 — kill 직전 · 재기동 직후 · 10분 뒤 · 복구 뒤에 반복
SELECT STATUS, COMPLETION_ATTEMPTS, COUNT(*) AS publications, MIN(PUBLICATION_DATE) AS oldest
FROM EVENT_PUBLICATION
GROUP BY STATUS, COMPLETION_ATTEMPTS;

-- [D] 중복이 날 뻔한 건수 — 재기동 직후, 재제출 전에 한 번
-- 남은 기록 중 알림이 이미 저장된 건은 재제출되면 같은 알림을 다시 넣으려 한다. (type, ref_id) 유니크가 이를 1건으로 막는다
SELECT COUNT(*) AS stuck_publications,
       SUM(n.notification_id IS NOT NULL) AS already_notified
FROM EVENT_PUBLICATION e
LEFT JOIN notification n
       ON n.type = 'TRANSFER'
      AND n.ref_id = CAST(JSON_UNQUOTE(JSON_EXTRACT(e.SERIALIZED_EVENT, '$.refId')) AS UNSIGNED)
WHERE e.EVENT_TYPE = 'com.shinhan.corebank.transfer.api.TransferSettled';

-- [E] 판정 — EVENT_PUBLICATION 이 0행이 된 뒤
-- 유실: 즉시이체 한 건마다 TRANSFER 알림 한 건이 있어야 한다
SELECT COUNT(*) AS transfers,
       SUM(t.status = 'SUCCESS') AS succeeded,
       SUM(t.status <> 'SUCCESS') AS not_succeeded,
       SUM(n.notification_id IS NULL) AS missing_notifications
FROM transfer t
LEFT JOIN notification n ON n.type = 'TRANSFER' AND n.ref_id = t.transfer_id
WHERE t.transfer_id > @baseline_transfer_id
  AND t.transfer_type = 'IMMEDIATE';

-- 중복: 같은 (type, ref_id) 알림이 2건 이상인 묶음
SELECT COUNT(*) AS duplicated_groups
FROM (SELECT ref_id FROM notification
      WHERE type = 'TRANSFER' AND ref_id > @baseline_transfer_id
      GROUP BY ref_id HAVING COUNT(*) > 1) d;

-- 포기한 기록: 재제출 상한(3번)을 넘긴 FAILED
SELECT COUNT(*) AS abandoned
FROM EVENT_PUBLICATION
WHERE STATUS = 'FAILED' AND COMPLETION_ATTEMPTS >= 4;
