-- ====================================================================
-- R__seed_master_data.sql  (Repeatable — 체크섬이 바뀔 때마다 재실행)
-- 약관·상품 마스터 시드. 개발 중 값이 자주 바뀌므로 V 가 아닌 R 로 둔다.
-- 반드시 멱등해야 하므로 전부 ON DUPLICATE KEY UPDATE 로 작성한다.
--
-- [금리 설계 규칙] 상품 목록/상세 응답이 서로 어긋나지 않도록 다음을 항상 만족시킨다.
--   base_rate = 최소 가입기간 구간의 product_rate_tier.rate
--   max_rate  = MAX(product_rate_tier.rate) + SUM(product_preferential_rate.rate)
-- 우대금리는 스키마상 상품 단위(product_id + condition_code)라 기간별로 다르게 줄 수 없다.
--
-- [정렬 커버리지] ProductPersistenceAdapter 의 정렬 기준은 다음과 같다.
--   RATE -> max_rate DESC / NAME -> product_name ASC / NEW -> sale_start_date DESC
-- 특히 NEW 는 new_flag 가 아니라 sale_start_date 를 본다. 12건 전부 서로 다른
-- sale_start_date 를 부여해 세 정렬이 확실히 다른 결과를 내도록 했다.
-- ====================================================================

-- 상품 (product_code 가 UK)
INSERT INTO product
  (product_code, product_name, product_group, deposit_type, summary, description,
   eligibility, subscription_restrictions, notices,
   base_rate, max_rate, min_amount, max_amount, amount_unit, min_term_months, max_term_months,
   interest_pay_type, sale_status, sale_start_date, sale_end_date, new_flag, single_account_limit,
   created_at, updated_at) VALUES
  ('PRD_YOUTH_SAVE', '청년 희망 적금', 'SAVINGS', 'INSTALLMENT', '청년 대상 우대 적금',
   '만 19~34세 대상 정액적립식 적금',
   '가입일 현재 만 19세 이상 만 34세 이하의 실명의 개인 (1인 1계좌)',
   '["1인 1계좌만 가입할 수 있습니다.","월 납입 한도를 초과하는 납입은 불가합니다.","만기 전 중도해지 시 중도해지이율이 적용됩니다."]',
   '["이 상품의 금리는 가입일 기준으로 확정되며, 가입 후 시장금리 변동과 무관하게 유지됩니다.","우대금리는 조건 충족 여부에 따라 적용되며, 조건 미충족 시 기본금리만 적용됩니다.","만기 전 중도해지 시 약정이율보다 낮은 중도해지이율이 적용되어 이자가 줄어듭니다.","세금은 관련 세법에 따라 부과되며, 표시된 금리는 세전 기준입니다."]',
   3.20, 4.50,     10000,    1000000,   10000,  6, 36, 'SIMPLE',   'ON_SALE', '2026-08-05', NULL,         TRUE,  TRUE,  '2026-08-01 00:00:00.000000', '2026-08-01 00:00:00.000000'),

  ('PRD_BASIC_DEP', '기본 정기예금', 'DEPOSIT', 'LUMP_SUM', '기본형 정기예금',
   '간편하게 가입할 수 있는 기본형 정기예금으로, 안정적인 확정 금리를 제공합니다.',
   '실명의 개인 및 개인사업자',
   '["가입 후 추가 납입은 불가합니다.","만기 전 중도해지 시 중도해지이율이 적용됩니다."]',
   '["이 상품의 금리는 가입일 기준으로 확정되며, 가입 후 시장금리 변동과 무관하게 유지됩니다.","우대금리는 조건 충족 여부에 따라 적용되며, 조건 미충족 시 기본금리만 적용됩니다.","만기 전 중도해지 시 약정이율보다 낮은 중도해지이율이 적용되어 이자가 줄어듭니다.","세금은 관련 세법에 따라 부과되며, 표시된 금리는 세전 기준입니다."]',
   2.80, 3.30,    100000,  100000000,  100000,  6, 36, 'SIMPLE',   'ON_SALE', '2026-08-01', NULL,         FALSE, FALSE, '2026-08-01 00:00:00.000000', '2026-08-01 00:00:00.000000'),

  ('PRD_CORE_DEP', '코어 정기예금', 'DEPOSIT', 'LUMP_SUM', '여윳돈을 안전하게 굴리는 기본 정기예금입니다.',
   '목돈을 정해진 기간 동안 예치하고 만기에 원금과 이자를 함께 받는 거치식 정기예금입니다.',
   '실명의 개인 및 개인사업자',
   '["가입 후 추가 납입은 불가합니다.","만기 전 중도해지 시 중도해지이율이 적용됩니다."]',
   '["이 상품의 금리는 가입일 기준으로 확정되며, 가입 후 시장금리 변동과 무관하게 유지됩니다.","우대금리는 조건 충족 여부에 따라 적용되며, 조건 미충족 시 기본금리만 적용됩니다.","만기 전 중도해지 시 약정이율보다 낮은 중도해지이율이 적용되어 이자가 줄어듭니다.","세금은 관련 세법에 따라 부과되며, 표시된 금리는 세전 기준입니다."]',
   3.10, 3.85,    100000,  500000000,  100000,  6, 36, 'SIMPLE',   'ON_SALE', '2026-07-20', NULL,         FALSE, FALSE, '2026-08-01 00:00:00.000000', '2026-08-01 00:00:00.000000'),

  ('PRD_FREE_SAVE', '코어 자유적금', 'SAVINGS', 'INSTALLMENT', '매달 원하는 금액을 자유롭게 납입하는 적금입니다.',
   '매월 한도 안에서 원하는 금액을 자유롭게 납입할 수 있는 자유적립식 적금입니다.',
   '실명의 개인',
   '["월 납입 한도를 초과하는 납입은 불가합니다.","만기 전 중도해지 시 중도해지이율이 적용됩니다."]',
   '["이 상품의 금리는 가입일 기준으로 확정되며, 가입 후 시장금리 변동과 무관하게 유지됩니다.","우대금리는 조건 충족 여부에 따라 적용되며, 조건 미충족 시 기본금리만 적용됩니다.","만기 전 중도해지 시 약정이율보다 낮은 중도해지이율이 적용되어 이자가 줄어듭니다.","세금은 관련 세법에 따라 부과되며, 표시된 금리는 세전 기준입니다."]',
   3.70, 4.20,     10000,    3000000,   10000, 12, 36, 'SIMPLE',   'ON_SALE', '2026-07-25', NULL,         TRUE,  FALSE, '2026-08-01 00:00:00.000000', '2026-08-01 00:00:00.000000'),

  ('PRD_LARGE_DEP', '코어 목돈예금', 'DEPOSIT', 'LUMP_SUM', '목돈을 장기간 예치할수록 금리가 높아지는 예금입니다.',
   '예치 기간이 길수록 높은 금리를 적용하는 장기 거치식 정기예금입니다.',
   '실명의 개인 및 개인사업자',
   '["가입 후 추가 납입은 불가합니다.","만기 전 중도해지 시 중도해지이율이 적용됩니다."]',
   '["이 상품의 금리는 가입일 기준으로 확정되며, 가입 후 시장금리 변동과 무관하게 유지됩니다.","우대금리는 조건 충족 여부에 따라 적용되며, 조건 미충족 시 기본금리만 적용됩니다.","만기 전 중도해지 시 약정이율보다 낮은 중도해지이율이 적용되어 이자가 줄어듭니다.","세금은 관련 세법에 따라 부과되며, 표시된 금리는 세전 기준입니다."]',
   3.50, 4.05,   1000000, 1000000000,  100000, 12, 60, 'SIMPLE',   'ON_SALE', '2026-07-18', NULL,         FALSE, FALSE, '2026-08-01 00:00:00.000000', '2026-08-01 00:00:00.000000'),

  ('PRD_REGULAR_SAVE', '코어 정기적금', 'SAVINGS', 'INSTALLMENT', '매달 같은 금액을 납입해 만기에 목돈을 만드는 적금입니다.',
   '매월 약정한 금액을 동일하게 납입하는 정액적립식 적금입니다.',
   '실명의 개인',
   '["월 납입 한도를 초과하는 납입은 불가합니다.","만기 전 중도해지 시 중도해지이율이 적용됩니다."]',
   '["이 상품의 금리는 가입일 기준으로 확정되며, 가입 후 시장금리 변동과 무관하게 유지됩니다.","우대금리는 조건 충족 여부에 따라 적용되며, 조건 미충족 시 기본금리만 적용됩니다.","만기 전 중도해지 시 약정이율보다 낮은 중도해지이율이 적용되어 이자가 줄어듭니다.","세금은 관련 세법에 따라 부과되며, 표시된 금리는 세전 기준입니다."]',
   3.80, 4.35,     50000,    2000000,   10000,  6, 24, 'SIMPLE',   'ON_SALE', '2026-07-22', NULL,         FALSE, FALSE, '2026-08-01 00:00:00.000000', '2026-08-01 00:00:00.000000'),

  ('PRD_SHORT_DEP', '코어 단기예금', 'DEPOSIT', 'LUMP_SUM', '짧은 기간 자금을 예치하기 좋은 단기 정기예금입니다.',
   '1개월부터 가입할 수 있어 단기 여유자금 운용에 적합한 거치식 정기예금입니다.',
   '실명의 개인 및 개인사업자',
   '["가입 후 추가 납입은 불가합니다.","만기 전 중도해지 시 중도해지이율이 적용됩니다."]',
   '["이 상품의 금리는 가입일 기준으로 확정되며, 가입 후 시장금리 변동과 무관하게 유지됩니다.","우대금리는 조건 충족 여부에 따라 적용되며, 조건 미충족 시 기본금리만 적용됩니다.","만기 전 중도해지 시 약정이율보다 낮은 중도해지이율이 적용되어 이자가 줄어듭니다.","세금은 관련 세법에 따라 부과되며, 표시된 금리는 세전 기준입니다."]',
   2.60, 3.40,    500000,  300000000,  100000,  1, 12, 'SIMPLE',   'ON_SALE', '2026-07-15', NULL,         FALSE, FALSE, '2026-08-01 00:00:00.000000', '2026-08-01 00:00:00.000000'),

  ('PRD_GOAL_SAVE', '코어 목표적금', 'SAVINGS', 'INSTALLMENT', '목표 금액을 정해 꾸준히 모으는 정기적금입니다.',
   '목표 금액을 설정하고 만기까지 꾸준히 납입하는 정액적립식 적금입니다.',
   '실명의 개인',
   '["월 납입 한도를 초과하는 납입은 불가합니다.","만기 전 중도해지 시 중도해지이율이 적용됩니다."]',
   '["이 상품의 금리는 가입일 기준으로 확정되며, 가입 후 시장금리 변동과 무관하게 유지됩니다.","우대금리는 조건 충족 여부에 따라 적용되며, 조건 미충족 시 기본금리만 적용됩니다.","만기 전 중도해지 시 약정이율보다 낮은 중도해지이율이 적용되어 이자가 줄어듭니다.","세금은 관련 세법에 따라 부과되며, 표시된 금리는 세전 기준입니다."]',
   3.95, 4.50,     30000,    5000000,   10000, 12, 36, 'SIMPLE',   'ON_SALE', '2026-07-28', NULL,         TRUE,  FALSE, '2026-08-01 00:00:00.000000', '2026-08-01 00:00:00.000000'),

  ('PRD_HOUSING_SAVE', '내집마련 적금', 'SAVINGS', 'INSTALLMENT', '주택 마련 자금을 장기간 모으는 복리 적금입니다.',
   '최장 60개월까지 가입할 수 있는 장기 정액적립식 적금으로, 이자를 복리로 계산합니다.',
   '실명의 개인 (1인 1계좌)',
   '["1인 1계좌만 가입할 수 있습니다.","월 납입 한도를 초과하는 납입은 불가합니다.","만기 전 중도해지 시 중도해지이율이 적용됩니다."]',
   '["이 상품의 금리는 가입일 기준으로 확정되며, 가입 후 시장금리 변동과 무관하게 유지됩니다.","우대금리는 조건 충족 여부에 따라 적용되며, 조건 미충족 시 기본금리만 적용됩니다.","만기 전 중도해지 시 약정이율보다 낮은 중도해지이율이 적용되어 이자가 줄어듭니다.","세금은 관련 세법에 따라 부과되며, 표시된 금리는 세전 기준입니다."]',
   3.40, 4.10,    100000,    1000000,   10000, 12, 60, 'COMPOUND', 'ON_SALE', '2026-08-10', NULL,         TRUE,  TRUE,  '2026-08-01 00:00:00.000000', '2026-08-01 00:00:00.000000'),

  ('PRD_SENIOR_DEP', '시니어 우대예금', 'DEPOSIT', 'LUMP_SUM', '만 50세 이상 고객을 위한 우대 정기예금입니다.',
   '장기 거래 고객에게 추가 우대금리를 제공하는 거치식 정기예금입니다.',
   '가입일 현재 만 50세 이상의 실명의 개인',
   '["가입 후 추가 납입은 불가합니다.","만기 전 중도해지 시 중도해지이율이 적용됩니다."]',
   '["이 상품의 금리는 가입일 기준으로 확정되며, 가입 후 시장금리 변동과 무관하게 유지됩니다.","우대금리는 조건 충족 여부에 따라 적용되며, 조건 미충족 시 기본금리만 적용됩니다.","만기 전 중도해지 시 약정이율보다 낮은 중도해지이율이 적용되어 이자가 줄어듭니다.","세금은 관련 세법에 따라 부과되며, 표시된 금리는 세전 기준입니다."]',
   3.00, 3.60,   1000000,  200000000,  100000, 12, 36, 'SIMPLE',   'ON_SALE', '2026-07-10', NULL,         FALSE, FALSE, '2026-08-01 00:00:00.000000', '2026-08-01 00:00:00.000000'),

  ('PRD_SALARY_SAVE', '급여이체 적금', 'SAVINGS', 'INSTALLMENT', '급여이체 실적으로 최고 우대금리를 받는 적금입니다.',
   '급여이체와 카드 결제 실적을 충족하면 높은 우대금리를 제공하는 정액적립식 적금입니다.',
   '급여이체 실적을 보유한 실명의 개인',
   '["월 납입 한도를 초과하는 납입은 불가합니다.","만기 전 중도해지 시 중도해지이율이 적용됩니다."]',
   '["이 상품의 금리는 가입일 기준으로 확정되며, 가입 후 시장금리 변동과 무관하게 유지됩니다.","우대금리는 조건 충족 여부에 따라 적용되며, 조건 미충족 시 기본금리만 적용됩니다.","만기 전 중도해지 시 약정이율보다 낮은 중도해지이율이 적용되어 이자가 줄어듭니다.","세금은 관련 세법에 따라 부과되며, 표시된 금리는 세전 기준입니다."]',
   3.60, 4.40,     50000,    1500000,   10000,  6, 24, 'SIMPLE',   'ON_SALE', '2026-08-14', NULL,         TRUE,  FALSE, '2026-08-01 00:00:00.000000', '2026-08-01 00:00:00.000000'),

  ('PRD_PRIME_DEP', '프라임 정기예금', 'DEPOSIT', 'LUMP_SUM', '1천만원 이상 예치 고객을 위한 한정 판매 예금입니다.',
   '최소 1천만원 이상 예치해야 가입할 수 있는 한정 판매 거치식 정기예금입니다. 이자를 복리로 계산합니다.',
   '1천만원 이상 예치 가능한 실명의 개인 및 개인사업자 (1인 1계좌)',
   '["1인 1계좌만 가입할 수 있습니다.","가입 후 추가 납입은 불가합니다.","만기 전 중도해지 시 중도해지이율이 적용됩니다."]',
   '["이 상품의 금리는 가입일 기준으로 확정되며, 가입 후 시장금리 변동과 무관하게 유지됩니다.","우대금리는 조건 충족 여부에 따라 적용되며, 조건 미충족 시 기본금리만 적용됩니다.","만기 전 중도해지 시 약정이율보다 낮은 중도해지이율이 적용되어 이자가 줄어듭니다.","세금은 관련 세법에 따라 부과되며, 표시된 금리는 세전 기준입니다."]',
   3.20, 3.70,  10000000, 1000000000, 1000000, 12, 36, 'COMPOUND', 'ON_SALE', '2026-07-30', '2026-12-31', FALSE, TRUE,  '2026-08-01 00:00:00.000000', '2026-08-01 00:00:00.000000')
ON DUPLICATE KEY UPDATE
  product_name = VALUES(product_name), product_group = VALUES(product_group),
  deposit_type = VALUES(deposit_type), summary = VALUES(summary),
  description = VALUES(description), eligibility = VALUES(eligibility),
  subscription_restrictions = VALUES(subscription_restrictions), notices = VALUES(notices),
  base_rate = VALUES(base_rate), max_rate = VALUES(max_rate),
  min_amount = VALUES(min_amount), max_amount = VALUES(max_amount),
  amount_unit = VALUES(amount_unit), min_term_months = VALUES(min_term_months),
  max_term_months = VALUES(max_term_months), interest_pay_type = VALUES(interest_pay_type),
  sale_status = VALUES(sale_status), sale_start_date = VALUES(sale_start_date),
  sale_end_date = VALUES(sale_end_date), new_flag = VALUES(new_flag),
  single_account_limit = VALUES(single_account_limit), updated_at = VALUES(updated_at);

-- ====================================================================
-- 계좌번호 채번 기준 데이터
-- 상품마다 채번 행이 반드시 1건 있어야 한다. 없으면 가입 시 ACC9001 이 난다.
-- prefix 는 2x = 예금 / 3x = 적금, 10 = 입출금으로 고정한다.
-- 재실행 대비: last_sequence 는 ON DUPLICATE KEY UPDATE 에서 절대 건드리지 않는다
-- (이미 발급된 번호를 다시 발급해 account_number 가 충돌하는 것을 막는다).
-- ====================================================================
INSERT INTO account_number_sequence
  (bank_code, account_type, product_id, product_prefix, last_sequence, created_at, updated_at)
VALUES
  ('088', 'DEMAND_DEPOSIT', NULL, '10', 0,
   '2026-08-01 00:00:00.000000', '2026-08-01 00:00:00.000000')
ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at);

-- account_type 매핑은 ProductSubscriptionExecuteService.toAccountType() 과 같아야 한다.
-- product_id 는 환경마다 auto_increment 값이 달라 product_code 조인으로 얻는다.
INSERT INTO account_number_sequence
  (bank_code, account_type, product_id, product_prefix, last_sequence, created_at, updated_at)
SELECT '088',
       CASE p.product_group
            WHEN 'DEPOSIT' THEN 'TIME_DEPOSIT'
            ELSE 'INSTALLMENT_SAVINGS'
       END,
       p.product_id,
       s.product_prefix,
       0,
       '2026-08-01 00:00:00.000000',
       '2026-08-01 00:00:00.000000'
FROM product p
JOIN (
  SELECT 'PRD_BASIC_DEP'     AS product_code, '20' AS product_prefix
  UNION ALL SELECT 'PRD_CORE_DEP',     '21'
  UNION ALL SELECT 'PRD_LARGE_DEP',    '22'
  UNION ALL SELECT 'PRD_SHORT_DEP',    '23'
  UNION ALL SELECT 'PRD_SENIOR_DEP',   '24'
  UNION ALL SELECT 'PRD_PRIME_DEP',    '25'
  UNION ALL SELECT 'PRD_YOUTH_SAVE',   '30'
  UNION ALL SELECT 'PRD_FREE_SAVE',    '31'
  UNION ALL SELECT 'PRD_REGULAR_SAVE', '32'
  UNION ALL SELECT 'PRD_GOAL_SAVE',    '33'
  UNION ALL SELECT 'PRD_HOUSING_SAVE', '34'
  UNION ALL SELECT 'PRD_SALARY_SAVE',  '35'
) s ON s.product_code = p.product_code
ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at);

-- 상품 기간별 금리 (product_id + term_months 가 PK)
-- 각 상품의 최소 가입기간 구간 rate 가 product.base_rate 와 같아야 한다.
INSERT INTO product_rate_tier (product_id, term_months, rate)
SELECT p.product_id, t.term_months, t.rate
FROM product p
JOIN (
  SELECT 'PRD_YOUTH_SAVE'   AS product_code,  6 AS term_months, 3.20 AS rate
  UNION ALL SELECT 'PRD_YOUTH_SAVE',   12, 3.35
  UNION ALL SELECT 'PRD_YOUTH_SAVE',   24, 3.50
  UNION ALL SELECT 'PRD_YOUTH_SAVE',   36, 3.60

  UNION ALL SELECT 'PRD_BASIC_DEP',     6, 2.80
  UNION ALL SELECT 'PRD_BASIC_DEP',    12, 2.95
  UNION ALL SELECT 'PRD_BASIC_DEP',    24, 3.00
  UNION ALL SELECT 'PRD_BASIC_DEP',    36, 3.05

  UNION ALL SELECT 'PRD_CORE_DEP',      6, 3.10
  UNION ALL SELECT 'PRD_CORE_DEP',     12, 3.45
  UNION ALL SELECT 'PRD_CORE_DEP',     24, 3.55
  UNION ALL SELECT 'PRD_CORE_DEP',     36, 3.60

  UNION ALL SELECT 'PRD_FREE_SAVE',    12, 3.70
  UNION ALL SELECT 'PRD_FREE_SAVE',    24, 3.85
  UNION ALL SELECT 'PRD_FREE_SAVE',    36, 3.90

  UNION ALL SELECT 'PRD_LARGE_DEP',    12, 3.50
  UNION ALL SELECT 'PRD_LARGE_DEP',    24, 3.65
  UNION ALL SELECT 'PRD_LARGE_DEP',    36, 3.70
  UNION ALL SELECT 'PRD_LARGE_DEP',    60, 3.75

  UNION ALL SELECT 'PRD_REGULAR_SAVE',  6, 3.80
  UNION ALL SELECT 'PRD_REGULAR_SAVE', 12, 3.95
  UNION ALL SELECT 'PRD_REGULAR_SAVE', 24, 3.90

  UNION ALL SELECT 'PRD_SHORT_DEP',     1, 2.60
  UNION ALL SELECT 'PRD_SHORT_DEP',     3, 2.85
  UNION ALL SELECT 'PRD_SHORT_DEP',     6, 3.05
  UNION ALL SELECT 'PRD_SHORT_DEP',    12, 3.15

  UNION ALL SELECT 'PRD_GOAL_SAVE',    12, 3.95
  UNION ALL SELECT 'PRD_GOAL_SAVE',    24, 4.00
  UNION ALL SELECT 'PRD_GOAL_SAVE',    36, 4.05

  UNION ALL SELECT 'PRD_HOUSING_SAVE', 12, 3.40
  UNION ALL SELECT 'PRD_HOUSING_SAVE', 24, 3.55
  UNION ALL SELECT 'PRD_HOUSING_SAVE', 36, 3.65
  UNION ALL SELECT 'PRD_HOUSING_SAVE', 60, 3.70

  UNION ALL SELECT 'PRD_SENIOR_DEP',   12, 3.00
  UNION ALL SELECT 'PRD_SENIOR_DEP',   24, 3.15
  UNION ALL SELECT 'PRD_SENIOR_DEP',   36, 3.25

  UNION ALL SELECT 'PRD_SALARY_SAVE',   6, 3.60
  UNION ALL SELECT 'PRD_SALARY_SAVE',  12, 3.80
  UNION ALL SELECT 'PRD_SALARY_SAVE',  24, 3.90

  UNION ALL SELECT 'PRD_PRIME_DEP',    12, 3.20
  UNION ALL SELECT 'PRD_PRIME_DEP',    24, 3.35
  UNION ALL SELECT 'PRD_PRIME_DEP',    36, 3.40
) t ON t.product_code = p.product_code
ON DUPLICATE KEY UPDATE rate = VALUES(rate);

-- 상품 우대금리 (product_id + condition_code 가 PK)
-- 상품별 rate 합계 = product.max_rate - MAX(product_rate_tier.rate) 가 되도록 맞춘다.
INSERT INTO product_preferential_rate (product_id, condition_code, condition_name, rate)
SELECT p.product_id, r.condition_code, r.condition_name, r.rate
FROM product p
JOIN (
  SELECT 'PRD_YOUTH_SAVE'   AS product_code, 'PREF_YOUTH'          AS condition_code, '만 19~34세 청년 고객'          AS condition_name, 0.50 AS rate
  UNION ALL SELECT 'PRD_YOUTH_SAVE',   'PREF_SALARY',        '급여이체 실적 보유',            0.20
  UNION ALL SELECT 'PRD_YOUTH_SAVE',   'PREF_AUTO_TRANSFER', '자동이체 3건 이상 등록',        0.20

  UNION ALL SELECT 'PRD_BASIC_DEP',    'PREF_ONLINE',        '비대면 채널 가입',              0.15
  UNION ALL SELECT 'PRD_BASIC_DEP',    'PREF_MARKETING',     '마케팅 정보 수신 동의',         0.10

  UNION ALL SELECT 'PRD_CORE_DEP',     'PREF_ONLINE',        '비대면 채널 가입',              0.15
  UNION ALL SELECT 'PRD_CORE_DEP',     'PREF_MARKETING',     '마케팅 정보 수신 동의',         0.10

  UNION ALL SELECT 'PRD_FREE_SAVE',    'PREF_SALARY',        '급여이체 실적 보유',            0.20
  UNION ALL SELECT 'PRD_FREE_SAVE',    'PREF_ONLINE',        '비대면 채널 가입',              0.10

  UNION ALL SELECT 'PRD_LARGE_DEP',    'PREF_LONG_TERM',     '3년 이상 거래 고객',            0.20
  UNION ALL SELECT 'PRD_LARGE_DEP',    'PREF_ONLINE',        '비대면 채널 가입',              0.10

  UNION ALL SELECT 'PRD_REGULAR_SAVE', 'PREF_SALARY',        '급여이체 실적 보유',            0.20
  UNION ALL SELECT 'PRD_REGULAR_SAVE', 'PREF_AUTO_TRANSFER', '자동이체 3건 이상 등록',        0.10
  UNION ALL SELECT 'PRD_REGULAR_SAVE', 'PREF_ONLINE',        '비대면 채널 가입',              0.10

  UNION ALL SELECT 'PRD_SHORT_DEP',    'PREF_ONLINE',        '비대면 채널 가입',              0.15
  UNION ALL SELECT 'PRD_SHORT_DEP',    'PREF_FIRST',         '첫 거래 고객',                  0.10

  UNION ALL SELECT 'PRD_GOAL_SAVE',    'PREF_AUTO_TRANSFER', '자동이체 3건 이상 등록',        0.20
  UNION ALL SELECT 'PRD_GOAL_SAVE',    'PREF_SALARY',        '급여이체 실적 보유',            0.15
  UNION ALL SELECT 'PRD_GOAL_SAVE',    'PREF_MARKETING',     '마케팅 정보 수신 동의',         0.10

  UNION ALL SELECT 'PRD_HOUSING_SAVE', 'PREF_FIRST',         '첫 거래 고객',                  0.20
  UNION ALL SELECT 'PRD_HOUSING_SAVE', 'PREF_AUTO_TRANSFER', '자동이체 3건 이상 등록',        0.20

  UNION ALL SELECT 'PRD_SENIOR_DEP',   'PREF_LONG_TERM',     '3년 이상 거래 고객',            0.25
  UNION ALL SELECT 'PRD_SENIOR_DEP',   'PREF_MARKETING',     '마케팅 정보 수신 동의',         0.10

  UNION ALL SELECT 'PRD_SALARY_SAVE',  'PREF_SALARY',        '급여이체 실적 보유',            0.35
  UNION ALL SELECT 'PRD_SALARY_SAVE',  'PREF_CARD',          '카드 결제 실적 월 30만원 이상', 0.15

  UNION ALL SELECT 'PRD_PRIME_DEP',    'PREF_LONG_TERM',     '3년 이상 거래 고객',            0.20
  UNION ALL SELECT 'PRD_PRIME_DEP',    'PREF_ONLINE',        '비대면 채널 가입',              0.10
) r ON r.product_code = p.product_code
ON DUPLICATE KEY UPDATE condition_name = VALUES(condition_name), rate = VALUES(rate);

-- 상품-약관 연결 (product_id + terms_id 가 PK). 필수 동의 여부는 terms.is_required 기준.
-- 상품군별로 대응하는 약관만 연결한다 (적금 → TERMS_SAVINGS, 예금 → TERMS_DEPOSIT).
-- display_order: 지금은 상품당 약관이 1건뿐이라 전부 1.
--
-- docs/flyway_file_role_guide.md는 "약관처럼 버전 이력이 중요한 데이터는 R이 아니라 V를 쓰라"고
-- 하지만(terms 테이블이 793a467에서 그 이유로 R->V 이관됨), product_terms는 이 파일이 위에서 시딩하는
-- product.product_id를 FK로 참조한다. Flyway는 V를 전부 적용한 뒤에야 R을 실행하므로, product_terms를
-- 별도 V 스크립트로 빼면 product 로우가 아직 없는 시점에 실행되어 조용히 0건만 INSERT되고 끝난다
-- (product 자체도 V로 옮기지 않는 한 근본 해결 불가 — 이건 이 파일의 원래 스코프를 넘는 변경이라
-- 보류함). 그래서 product_terms는 예외적으로 R에 남기고, DELETE 후 재삽입으로 버전 변경에 대응한다:
-- terms.version이 바뀌면 terms_id도 함께 바뀌어(PK: product_id+terms_id) ON DUPLICATE KEY UPDATE가
-- 걸리지 않고 새 행으로 INSERT되므로, 매핑 대상 product_code x terms_code의 기존 연결을 먼저 지운다.
DELETE pt FROM product_terms pt
JOIN product p ON p.product_id = pt.product_id
JOIN terms t ON t.terms_id = pt.terms_id
JOIN (
  SELECT 'PRD_YOUTH_SAVE' AS product_code, 'TERMS_SAVINGS' AS terms_code
  UNION ALL SELECT 'PRD_BASIC_DEP',    'TERMS_DEPOSIT'
  UNION ALL SELECT 'PRD_CORE_DEP',     'TERMS_DEPOSIT'
  UNION ALL SELECT 'PRD_FREE_SAVE',    'TERMS_SAVINGS'
  UNION ALL SELECT 'PRD_LARGE_DEP',    'TERMS_DEPOSIT'
  UNION ALL SELECT 'PRD_REGULAR_SAVE', 'TERMS_SAVINGS'
  UNION ALL SELECT 'PRD_SHORT_DEP',    'TERMS_DEPOSIT'
  UNION ALL SELECT 'PRD_GOAL_SAVE',    'TERMS_SAVINGS'
  UNION ALL SELECT 'PRD_HOUSING_SAVE', 'TERMS_SAVINGS'
  UNION ALL SELECT 'PRD_SENIOR_DEP',   'TERMS_DEPOSIT'
  UNION ALL SELECT 'PRD_SALARY_SAVE',  'TERMS_SAVINGS'
  UNION ALL SELECT 'PRD_PRIME_DEP',    'TERMS_DEPOSIT'
) mapping ON mapping.product_code = p.product_code AND mapping.terms_code = t.terms_code;

INSERT INTO product_terms (product_id, terms_id, display_order)
SELECT p.product_id, tm.terms_id, 1
FROM product p
JOIN (
  SELECT 'PRD_YOUTH_SAVE' AS product_code, 'TERMS_SAVINGS' AS terms_code
  UNION ALL SELECT 'PRD_BASIC_DEP',    'TERMS_DEPOSIT'
  UNION ALL SELECT 'PRD_CORE_DEP',     'TERMS_DEPOSIT'
  UNION ALL SELECT 'PRD_FREE_SAVE',    'TERMS_SAVINGS'
  UNION ALL SELECT 'PRD_LARGE_DEP',    'TERMS_DEPOSIT'
  UNION ALL SELECT 'PRD_REGULAR_SAVE', 'TERMS_SAVINGS'
  UNION ALL SELECT 'PRD_SHORT_DEP',    'TERMS_DEPOSIT'
  UNION ALL SELECT 'PRD_GOAL_SAVE',    'TERMS_SAVINGS'
  UNION ALL SELECT 'PRD_HOUSING_SAVE', 'TERMS_SAVINGS'
  UNION ALL SELECT 'PRD_SENIOR_DEP',   'TERMS_DEPOSIT'
  UNION ALL SELECT 'PRD_SALARY_SAVE',  'TERMS_SAVINGS'
  UNION ALL SELECT 'PRD_PRIME_DEP',    'TERMS_DEPOSIT'
) mapping ON mapping.product_code = p.product_code
JOIN terms tm ON tm.terms_code = mapping.terms_code AND tm.version = 'v1.0'
ON DUPLICATE KEY UPDATE terms_id = VALUES(terms_id), display_order = VALUES(display_order);
