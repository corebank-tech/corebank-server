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
  UNION ALL
  SELECT 'PRD_BASIC_DEP', 'TERMS_DEPOSIT'
) mapping ON mapping.product_code = p.product_code AND mapping.terms_code = t.terms_code;

INSERT INTO product_terms (product_id, terms_id, display_order)
SELECT p.product_id, tm.terms_id, 1
FROM product p
JOIN (
  SELECT 'PRD_YOUTH_SAVE' AS product_code, 'TERMS_SAVINGS' AS terms_code
  UNION ALL
  SELECT 'PRD_BASIC_DEP', 'TERMS_DEPOSIT'
) mapping ON mapping.product_code = p.product_code
JOIN terms tm ON tm.terms_code = mapping.terms_code AND tm.version = 'v1.0'
ON DUPLICATE KEY UPDATE terms_id = VALUES(terms_id), display_order = VALUES(display_order);
