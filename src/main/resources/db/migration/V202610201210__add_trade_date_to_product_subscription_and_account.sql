-- 정보계 ETL(PH-76)이 SUBSCRIBE/CLOSE 이벤트 기준일로 읽는 논리 영업일 컬럼. 설계: docs/phase2/info_mart.md §3-0, §3-3.
ALTER TABLE product_subscription
    ADD COLUMN subscription_trade_date DATE NULL COMMENT '가입 논리 영업일. 가입 트랜잭션이 읽은 BusinessDateProvider.today()' AFTER subscribed_at;

ALTER TABLE account
    ADD COLUMN closed_trade_date DATE NULL COMMENT '해지 논리 영업일. 해지 트랜잭션이 읽은 BusinessDateProvider.today()' AFTER closed_date;

-- 기존 가입 행은 가입 시각을 영업일로 올린 날짜로 채운다(주말과 holiday는 다음 영업일). COB 전환 뒤 마감 중 가입은 달력 기준이라 하루 어긋날 수 있다.
WITH RECURSIVE offsets (n) AS (
    SELECT 0 UNION ALL SELECT n + 1 FROM offsets WHERE n < 30
)
UPDATE product_subscription ps
SET ps.subscription_trade_date = (
    SELECT MIN(DATE(ps.subscribed_at) + INTERVAL o.n DAY)
    FROM offsets o
    WHERE WEEKDAY(DATE(ps.subscribed_at) + INTERVAL o.n DAY) < 5
      AND DATE(ps.subscribed_at) + INTERVAL o.n DAY NOT IN (SELECT holiday_date FROM holiday)
)
WHERE ps.subscription_trade_date IS NULL;
