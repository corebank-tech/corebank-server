-- ====================================================================
-- R__seed_gl_account.sql  (Repeatable — 체크섬이 바뀔 때마다 재실행)
-- 계정과목 시드 (P3, PH-20 / #451)
--
-- R__ 로 두는 이유: 계정과목은 운영에도 있어야 하는 마스터 데이터이고, 2차 동안
-- 계정이 늘거나 이름이 다듬어질 수 있다. V__ 는 적용 후 수정이 불가능해서 맞지 않고,
-- local-demo-data.sql 은 prod 에 적재되지 않아 맞지 않는다
-- (flyway_file_role_guide.md §1·§2·§3).
--
-- R__seed_master_data.sql 에 넣지 않고 파일을 나눈 이유: 그쪽은 헤더가 "약관·상품
-- 마스터"로 범위를 선언하고 있고 327줄이다. 섞으면 계정 한 줄을 고칠 때마다 상품
-- 시드 전체가 재실행된다.
--
-- 재실행되므로 반드시 멱등해야 한다 — 전부 ON DUPLICATE KEY UPDATE 로 쓴다.
--
-- [코드 체계] 대분류1 + 중분류2 + 세분류2 = 5자리. 첫 자리가 분류와 1:1 로 대응한다.
--   1 자산 / 2 부채 / 3 자본 / 4 수익 / 5 비용
--   정상잔액은 자산·비용이 차변(DEBIT), 부채·자본·수익이 대변(CREDIT)이다.
--
-- [정본 대조] 코드·명칭·분류·정상잔액은 FE 시산표 화면이 먼저 세운 목
--   (corebank-fe `src/entities/gl/api/ph28-trial-balance.ts` 의 GL_ACCOUNTS)과 같아야 한다.
--   화면이 API 계약을 먼저 제안한 구조라 두 벌이 갈리면 시산표가 어긋난다.
--
-- [포함 근거] #451 이 포함을 명시한 여섯 계정이 전부 들어 있다 —
--   예수금(20100) · 미결제타점권(10200) · 이자비용(50100) · 원천세예수금(20200) ·
--   개시잔액(30100) · 현금및현금성자산(10100). 나머지는 그 여섯이 서려면 상대 계정이
--   필요해서 채운 것이다.
-- ====================================================================

INSERT INTO gl_account (account_code, account_name, account_class, normal_balance) VALUES
  -- 1 자산 — 정상잔액 차변
  ('10100', '현금및현금성자산', 'ASSET',     'DEBIT'),
  ('10200', '미결제타점권',     'ASSET',     'DEBIT'),
  ('10300', '예치금',           'ASSET',     'DEBIT'),
  ('10400', '미수이자',         'ASSET',     'DEBIT'),
  -- 2 부채 — 정상잔액 대변
  ('20100', '예수금',           'LIABILITY', 'CREDIT'),
  ('20200', '원천세예수금',     'LIABILITY', 'CREDIT'),
  ('20300', '미지급이자',       'LIABILITY', 'CREDIT'),
  ('20400', '미지급금',         'LIABILITY', 'CREDIT'),
  ('20500', '가수금',           'LIABILITY', 'CREDIT'),
  -- 3 자본 — 정상잔액 대변
  ('30100', '개시잔액',         'EQUITY',    'CREDIT'),
  -- 4 수익 — 정상잔액 대변
  ('40100', '이자수익',         'REVENUE',   'CREDIT'),
  ('40200', '수수료수익',       'REVENUE',   'CREDIT'),
  -- 5 비용 — 정상잔액 차변
  ('50100', '이자비용',         'EXPENSE',   'DEBIT'),
  ('50200', '수수료비용',       'EXPENSE',   'DEBIT'),
  ('50300', '세금과공과',       'EXPENSE',   'DEBIT')
ON DUPLICATE KEY UPDATE
  account_name   = VALUES(account_name),
  account_class  = VALUES(account_class),
  normal_balance = VALUES(normal_balance);
