-- ====================================================================
-- V202608041233__drop_product_terms_required.sql
-- 상품-약관 필수여부 오버라이드 제거 (P3)
--
-- product_terms.required는 상품별로 terms.is_required를 오버라이드하기 위해
-- 만들어졌으나, 현재 요구사항에서는 불필요한 것으로 확인되어 제거한다.
-- 필수 동의 여부는 앞으로 terms.is_required 단일 기준으로만 판단한다.
-- ====================================================================

ALTER TABLE product_terms DROP COLUMN required;
