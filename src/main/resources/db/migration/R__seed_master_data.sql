-- ====================================================================
-- R__seed_master_data.sql  (Repeatable — 체크섬이 바뀔 때마다 재실행)
-- 약관·상품 마스터 시드. 개발 중 값이 자주 바뀌므로 V 가 아닌 R 로 둔다.
-- 반드시 멱등해야 하므로 전부 ON DUPLICATE KEY UPDATE 로 작성한다.
-- ====================================================================

-- 상품 (product_code 가 UK)
INSERT INTO product
  (product_code, product_name, product_group, deposit_type, summary, base_rate, max_rate,
   min_amount, max_amount, amount_unit, min_term_months, max_term_months,
   interest_pay_type, sale_status, new_flag, single_account_limit, created_at, updated_at) VALUES
  ('PRD_YOUTH_SAVE', '청년 희망 적금', 'SAVINGS', 'INSTALLMENT', '청년 대상 우대 적금',
   3.20, 4.50,  10000, 1000000,  10000, 6, 36, 'SIMPLE', 'ON_SALE', TRUE,  TRUE, '2026-08-01 00:00:00.000000', '2026-08-01 00:00:00.000000'),
  ('PRD_BASIC_DEP',  '기본 정기예금',  'DEPOSIT', 'LUMP_SUM', '기본형 정기예금',
   2.80, 3.30, 100000, 100000000, 100000, 6, 36, 'SIMPLE', 'ON_SALE', FALSE, FALSE, '2026-08-01 00:00:00.000000', '2026-08-01 00:00:00.000000')
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

-- 상품-약관 연결 (product_id + terms_id 가 PK). 필수 동의 여부는 terms.is_required 기준.
-- 상품군별로 대응하는 약관만 연결한다 (적금 → TERMS_SAVINGS, 예금 → TERMS_DEPOSIT).
INSERT INTO product_terms (product_id, terms_id)
SELECT p.product_id, tm.terms_id
FROM product p
JOIN (
  SELECT 'PRD_YOUTH_SAVE' AS product_code, 'TERMS_SAVINGS' AS terms_code
  UNION ALL
  SELECT 'PRD_BASIC_DEP', 'TERMS_DEPOSIT'
) mapping ON mapping.product_code = p.product_code
JOIN terms tm ON tm.terms_code = mapping.terms_code AND tm.version = 'v1.0'
ON DUPLICATE KEY UPDATE terms_id = VALUES(terms_id);

