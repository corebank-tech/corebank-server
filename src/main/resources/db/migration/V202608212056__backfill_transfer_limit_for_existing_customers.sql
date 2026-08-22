-- ====================================================================
-- V202608212056__backfill_transfer_limit_for_existing_customers.sql
-- 이체한도 (P1)
--
-- 회원가입 흐름에 기본값 부여(REQ-TRSF-029)를 연결하기 전에 가입한 고객은
-- transfer_limit 행이 없다. 그 고객들에게 정책 기본값 행을 채운다.
--
-- 시드 데이터가 아니라 일회성 보정이다(flyway_file_role_guide.md §1). 이 파일이
-- 만드는 행은 데모용 더미가 아니라 REQ-TRSF-029 가 진작 만들었어야 할 업무
-- 데이터이고, 한 번 채우면 이후로는 가입 트랜잭션이 만든다.
--
-- created_at 에 NOW() 를 쓰지 않는다. 팀 규칙상 시각 주입은 DB 가 아니라 자바가
-- 맡고(team_db_architecture_guide.md §3-①), 이 컬럼의 뜻이 "한도 최초 부여 일시"
-- (schema_reference.md)라 마이그레이션을 돌린 시각보다 고객의 가입 시각이 사실에
-- 가깝다. 부여됐어야 할 시점이 곧 가입 시점이다.
--
-- 금액은 POL-013(1회 100만) · POL-014(1일 500만)이며 자바쪽 기본값
-- TransferLimit.DEFAULT_ONE_TIME_LIMIT · DEFAULT_DAILY_LIMIT 와 같은 값이어야 한다.
--
-- 이미 행이 있는 고객은 NOT EXISTS 로 건너뛴다. 한도를 바꿔 둔 고객의 값을
-- 기본값으로 되돌리면 안 되므로 ON DUPLICATE KEY UPDATE 를 쓰지 않는다.
-- ====================================================================

INSERT INTO transfer_limit (customer_id, one_time_limit, daily_limit, created_at, updated_at)
SELECT c.customer_id,
       1000000,
       5000000,
       c.joined_at,
       c.joined_at
  FROM customer c
 WHERE NOT EXISTS (SELECT 1
                     FROM transfer_limit t
                    WHERE t.customer_id = c.customer_id);
