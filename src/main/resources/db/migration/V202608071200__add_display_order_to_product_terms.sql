-- product_terms.display_order: 상품 상세조회 화면에서 약관을 보여줄 순서.
-- P3(상품) 소유 컬럼이라 P6(terms) 협의 없이 추가한다.
ALTER TABLE product_terms
    ADD COLUMN display_order SMALLINT NULL AFTER terms_id;

-- 기존 시드 데이터는 상품당 약관이 1건뿐이라 전부 1로 채운다.
UPDATE product_terms
SET display_order = 1
WHERE display_order IS NULL;

ALTER TABLE product_terms
    MODIFY COLUMN display_order SMALLINT NOT NULL;