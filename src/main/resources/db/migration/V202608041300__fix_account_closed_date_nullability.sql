-- ====================================================================
-- V202608041300__fix_account_closed_date_nullability.sql
-- account 테이블 closed_date NULL 허용 원복 (V202608031651 의 NOT NULL 제약 롤백)
-- 제약조건 ck_account_closed_date 와의 논리 충돌을 해소하기 위함.
-- ====================================================================

ALTER TABLE account
    MODIFY COLUMN closed_date DATETIME(6) NULL
        COMMENT '계좌 해지일';
