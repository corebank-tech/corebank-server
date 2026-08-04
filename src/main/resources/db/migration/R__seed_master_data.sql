-- ====================================================================
-- R__seed_master_data.sql  (Repeatable — 체크섬이 바뀔 때마다 재실행)
-- 약관·상품 마스터 시드. 개발 중 값이 자주 바뀌므로 V 가 아닌 R 로 둔다.
-- 반드시 멱등해야 하므로 전부 ON DUPLICATE KEY UPDATE 로 작성한다.
-- ====================================================================

-- 약관 (terms_code + version 이 UK)
INSERT INTO terms (terms_code, version, terms_type, title, content, is_required, view_required, created_at, updated_at) VALUES
  ('TERMS_SERVICE',  'v1.0', 'SIGNUP',  '서비스 이용약관',        '(내용)', TRUE,  FALSE, '2026-08-01 00:00:00.000000', '2026-08-01 00:00:00.000000'),
  ('TERMS_PRIVACY',  'v1.0', 'SIGNUP',  '개인정보 수집·이용 동의', '(내용)', TRUE,  FALSE, '2026-08-01 00:00:00.000000', '2026-08-01 00:00:00.000000'),
  ('TERMS_MARKETING','v1.0', 'SIGNUP',  '마케팅 정보 수신 동의',   '(내용)', FALSE, FALSE, '2026-08-01 00:00:00.000000', '2026-08-01 00:00:00.000000'),
  ('TERMS_DEPOSIT',  'v1.0', 'PRODUCT', '예금거래 기본약관',       '(내용)', TRUE,  TRUE, '2026-08-01 00:00:00.000000', '2026-08-01 00:00:00.000000'),
  ('TERMS_SAVINGS',  'v1.0', 'PRODUCT', '적립식예금 약관',         '(내용)', TRUE,  TRUE, '2026-08-01 00:00:00.000000', '2026-08-01 00:00:00.000000')
ON DUPLICATE KEY UPDATE
  terms_type = VALUES(terms_type), title = VALUES(title), content = VALUES(content),
  is_required = VALUES(is_required), view_required = VALUES(view_required),
  updated_at = VALUES(updated_at);

-- 상품 (product_code 가 UK)
INSERT INTO product
  (product_code, product_name, product_group, deposit_type, summary, base_rate, max_rate,
   min_amount, max_amount, amount_unit, min_term_months, max_term_months,
   interest_pay_type, sale_status, new_flag, single_account_limit, created_at, updated_at) VALUES
  ('PRD_YOUTH_SAVE', '청년 희망 적금', 'SAVINGS', '적립식', '청년 대상 우대 적금',
   3.20, 4.50,  10000, 1000000,  10000, 6, 36, '단리', 'ON_SALE', TRUE,  TRUE, '2026-08-01 00:00:00.000000', '2026-08-01 00:00:00.000000'),
  ('PRD_BASIC_DEP',  '기본 정기예금',  'DEPOSIT', '거치식', '기본형 정기예금',
   2.80, 3.30, 100000, 100000000, 100000, 6, 36, '단리', 'ON_SALE', FALSE, FALSE, '2026-08-01 00:00:00.000000', '2026-08-01 00:00:00.000000')
ON DUPLICATE KEY UPDATE
  product_name = VALUES(product_name), product_group = VALUES(product_group),
  deposit_type = VALUES(deposit_type), summary = VALUES(summary),
  base_rate = VALUES(base_rate), max_rate = VALUES(max_rate),
  min_amount = VALUES(min_amount), max_amount = VALUES(max_amount),
  amount_unit = VALUES(amount_unit), min_term_months = VALUES(min_term_months),
  max_term_months = VALUES(max_term_months), interest_pay_type = VALUES(interest_pay_type),
  sale_status = VALUES(sale_status), new_flag = VALUES(new_flag),
  single_account_limit = VALUES(single_account_limit), updated_at = VALUES(updated_at);

-- 상품 기간별 금리 (product_id + term_months 가 PK)
INSERT INTO product_rate_tier (product_id, term_months, rate)
SELECT p.product_id, t.term_months, t.rate
FROM product p
JOIN (SELECT 6 AS term_months, 3.00 AS rate
      UNION ALL SELECT 12, 3.20
      UNION ALL SELECT 24, 3.40
      UNION ALL SELECT 36, 3.60) t
WHERE p.product_code = 'PRD_YOUTH_SAVE'
ON DUPLICATE KEY UPDATE rate = VALUES(rate);

-- 상품-약관 연결 (product_id + terms_id 가 PK)
INSERT INTO product_terms (product_id, terms_id, required)
SELECT p.product_id, tm.terms_id, TRUE
FROM product p
JOIN terms tm ON tm.terms_code IN ('TERMS_DEPOSIT', 'TERMS_SAVINGS') AND tm.version = 'v1.0'
ON DUPLICATE KEY UPDATE required = VALUES(required);
