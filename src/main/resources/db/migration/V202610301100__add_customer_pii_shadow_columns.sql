-- PH-18 (#736): 고객 개인정보 암호화 컬럼 추가
-- 기존 평문 컬럼은 유지하며, 백필 전까지 신규 컬럼은 NULL을 허용한다.

ALTER TABLE customer
    ADD COLUMN email_ciphertext VARCHAR(768) NULL
        COMMENT 'PH-18 이메일 AES-256-GCM 암호문 (v1:Base64URL)',
    ADD COLUMN phone_number_ciphertext VARCHAR(128) NULL
        COMMENT 'PH-18 휴대폰 번호 AES-256-GCM 암호문 (v1:Base64URL)',
    ADD COLUMN email_search_hash CHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NULL
        COMMENT 'PH-18 정규화 이메일 HMAC-SHA256 검색 해시',
    ALGORITHM=INSTANT;
