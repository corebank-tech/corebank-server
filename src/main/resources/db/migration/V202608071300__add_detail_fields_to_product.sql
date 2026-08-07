-- 상품 상세조회 응답 필드(eligibility/subscriptionRestrictions/notices) 대응 컬럼 추가.
-- subscription_restrictions/notices는 화면 표시 전용 문자열 배열이라 자식 테이블 대신
-- JSON 배열을 직렬화한 TEXT 컬럼으로 저장한다(애플리케이션의 StringListJsonConverter가 변환).
ALTER TABLE product
    ADD COLUMN eligibility TEXT NULL COMMENT '가입 자격 조건 안내 문구' AFTER description,
    ADD COLUMN subscription_restrictions TEXT NULL COMMENT '가입 제한 사항 목록(JSON 배열 문자열)' AFTER eligibility,
    ADD COLUMN notices TEXT NULL COMMENT '유의사항 목록(JSON 배열 문자열)' AFTER subscription_restrictions;