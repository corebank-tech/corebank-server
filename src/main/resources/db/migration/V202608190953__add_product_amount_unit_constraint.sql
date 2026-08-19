-- ====================================================================
-- V202608190953__add_product_amount_unit_constraint.sql
-- product.amount_unit 양수 제약 추가
--
-- 기존 ck_product_amount는 min_amount / max_amount만 검사하고 amount_unit은
-- 검사하지 않는다. 상품 생성·수정 API가 없어 마스터데이터가 수동 SQL로만 들어가는데,
-- amount_unit = 0이 들어가면 가입 사전 검증(POST /product-subscriptions/validation)의
-- 배수 검증에서 나머지 연산이 ArithmeticException을 던진다. (PR #147 R3)
--
-- 주의: Flyway 적용 후에는 이 파일을 수정하지 마십시오 (체크섬 불변).
--       변경은 새 V 파일에 ALTER 로 작성합니다.
-- ====================================================================

ALTER TABLE `product`

    ADD CONSTRAINT `ck_product_amount_unit`
        CHECK (`amount_unit` > 0);
