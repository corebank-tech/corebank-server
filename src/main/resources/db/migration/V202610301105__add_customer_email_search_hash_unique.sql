-- PH-18 (#736): 이메일 검색 해시 UNIQUE 인덱스
-- 컬럼 추가와 분리하여 온라인 인덱스 생성.

SET SESSION lock_wait_timeout = 10;

ALTER TABLE customer
    ADD UNIQUE INDEX uk_customer_email_search_hash (email_search_hash),
    ALGORITHM=INPLACE,
    LOCK=NONE;

SET SESSION lock_wait_timeout = DEFAULT;
