-- PH-18 (#736): 고객 연락처 암호화를 위한 확장 단계
--
-- 기존 email, phone_number 및 uk_customer_email은 유지한다.
-- 신규 컬럼은 백필 및 애플리케이션 전환 전까지 NULL을 허용한다.
-- 이 마이그레이션에서는 기존 고객 데이터를 수정하지 않는다.

ALTER TABLE customer
    ADD COLUMN email_ciphertext VARCHAR(768) NULL
        COMMENT 'PH-18 이메일 AES-256-GCM 암호문 (v1:Base64URL)',
    ADD COLUMN phone_number_ciphertext VARCHAR(128) NULL
        COMMENT 'PH-18 휴대폰 번호 AES-256-GCM 암호문 (v1:Base64URL)',
    ADD COLUMN email_search_hash CHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NULL
        COMMENT 'PH-18 정규화 이메일 HMAC-SHA256 검색 해시',
    ADD UNIQUE KEY uk_customer_email_search_hash (email_search_hash);
