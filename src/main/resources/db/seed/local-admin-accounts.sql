-- ====================================================================
-- 로컬 전용 관리자 계정 (PH-49a-①, #561)
--
-- local-demo-data.sql 과 나눠 둔 이유는 적재 환경이 다르기 때문이다. QA 데모
-- 데이터는 qa-seed 프로필로 운영 RDS 에도 올라가는데, 관리자 계정은 비밀번호가
-- 이 저장소에 평문으로 적혀 있어 운영에 들어가면 안 된다. 이 파일은
-- LocalAdminAccountLoader 가 local 프로필에서만 적재한다.
-- 운영 관리자 계정은 docs/phase2/admin_auth.md 의 SQL 로 직접 만든다.
--
-- 조회 전용과 변경 가능을 각각 한 명 둬서, PH-49a-② 의 권한 검사가 같은
-- /admin/** 요청을 어떻게 갈라내는지 로컬에서 바로 확인할 수 있게 한다.
-- 두 계정의 비밀번호는 Admin1234! 다.
--
-- 관리자는 이체를 하지 않아 transfer_limit 행을 만들지 않는다.
--
-- 권한은 admin_permission 에 행으로 들어간다(V202610152102). 재실행하면 그 계정의
-- 권한 행을 지우고 다시 넣어, 손으로 바꿔 둔 권한이 시드 상태로 돌아온다.
-- ====================================================================

INSERT INTO customer (
  user_id,
  password_hash,
  user_name,
  birth_date,
  email,
  phone_number,
  login_failure_count,
  account_locked,
  role,
  joined_at,
  created_at,
  updated_at
) VALUES
  (
    'adminreader',
    '$2a$10$E3kJMsKgJnqVwXhO/EFCzeviMHKdUD1KCc5r4yeqWxRQzuk5IwQWK',
    '감사담당자',
    '1985-03-10',
    'adminreader@example.com',
    '01099990001',
    0,
    FALSE,
    'ADMIN',
    '2026-07-01 09:00:00.000000',
    '2026-07-01 09:00:00.000000',
    '2026-07-01 09:00:00.000000'
  ),
  (
    'adminoperator',
    '$2a$10$E3kJMsKgJnqVwXhO/EFCzeviMHKdUD1KCc5r4yeqWxRQzuk5IwQWK',
    '운영담당자',
    '1983-07-21',
    'adminoperator@example.com',
    '01099990002',
    0,
    FALSE,
    'ADMIN',
    '2026-07-01 09:00:00.000000',
    '2026-07-01 09:00:00.000000',
    '2026-07-01 09:00:00.000000'
  )
ON DUPLICATE KEY UPDATE
  -- 실제 고객과 아이디·이메일이 겹치면 그 행은 건드리지 않고, 정확한 관리자
  -- 시드 계정의 역할과 잠금만 되돌린다.
  role = IF(
      user_id = VALUES(user_id) AND email = VALUES(email),
      VALUES(role),
      role
  ),
  login_failure_count = IF(
      user_id = VALUES(user_id) AND email = VALUES(email),
      VALUES(login_failure_count),
      login_failure_count
  ),
  account_locked = IF(
      user_id = VALUES(user_id) AND email = VALUES(email),
      VALUES(account_locked),
      account_locked
  );

-- 권한은 지우고 다시 넣는다. INSERT IGNORE 로만 두면 손으로 더해 둔 권한이 남아
-- 시드가 정한 조합과 달라진다.
--
-- 아래 세 문장이 user_id 와 email 을 함께 보는 이유는 위 ON DUPLICATE KEY UPDATE 와
-- 같다. user_id 만 맞는 실제 고객이 있으면 그 행은 역할이 바뀌지 않는데, 권한만
-- 시드 조합으로 덮이면 두 판정이 어긋난다(PR #572 리뷰).
DELETE ap FROM admin_permission ap
  JOIN customer c ON c.customer_id = ap.customer_id
 WHERE (c.user_id, c.email) IN (
           ('adminreader', 'adminreader@example.com'),
           ('adminoperator', 'adminoperator@example.com')
       );

-- 조회 전용 — PH-49a-② 의 DoD "변경 API 403" 을 확인하는 계정이다.
INSERT INTO admin_permission (customer_id, permission, granted_at)
SELECT c.customer_id, p.permission, '2026-07-01 09:00:00.000000'
  FROM customer c
  JOIN (
        SELECT 'GL_READ' AS permission
         UNION ALL SELECT 'CUSTOMER_READ'
         UNION ALL SELECT 'AUDIT_READ'
       ) p
 WHERE c.user_id = 'adminreader'
   AND c.email = 'adminreader@example.com';

-- 변경 가능 — FE 관리자 채널 시연용으로 5종을 전부 준다.
INSERT INTO admin_permission (customer_id, permission, granted_at)
SELECT c.customer_id, p.permission, '2026-07-01 09:00:00.000000'
  FROM customer c
  JOIN (
        SELECT 'GL_READ' AS permission
         UNION ALL SELECT 'GL_WRITE'
         UNION ALL SELECT 'CUSTOMER_READ'
         UNION ALL SELECT 'CUSTOMER_WRITE'
         UNION ALL SELECT 'AUDIT_READ'
       ) p
 WHERE c.user_id = 'adminoperator'
   AND c.email = 'adminoperator@example.com';
