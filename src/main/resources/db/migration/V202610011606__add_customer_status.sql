-- ====================================================================
-- V202610011606__add_customer_status.sql
-- 고객 (P6)
--
-- 관리자가 고객 계정을 이용정지(SUSPENDED)하거나 되살리는(ACTIVE) 상태를
-- 저장한다(#450). account_locked 는 비밀번호 5회 오류로 시스템이 거는 잠금이고,
-- status 는 운영 판단으로 관리자가 거는 정지라 의미가 달라 컬럼을 나눈다.
-- 두 상태는 겹칠 수 있다(정지된 고객이 비밀번호를 5회 틀린 경우).
--
-- NOT NULL DEFAULT 'ACTIVE' 로 추가하므로 기존 고객 행은 ACTIVE 로 채워지고,
-- 컬럼을 나열하는 시드·테스트 INSERT 도 수정 없이 ACTIVE 가 들어간다.
--
-- MySQL 은 DDL 이 암묵적 커밋이라 실패해도 롤백되지 않으므로, 한 파일이
-- 다루는 테이블을 customer 하나로 제한한다. 표시명 공통코드는
-- V202610011607 에 따로 둔다.
-- ====================================================================

ALTER TABLE customer
    ADD COLUMN status VARCHAR(12) NOT NULL DEFAULT 'ACTIVE'
        COMMENT '계정 상태. ACTIVE / SUSPENDED (관리자 이용정지)'
        AFTER account_locked,
    ADD CONSTRAINT ck_customer_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED'));
