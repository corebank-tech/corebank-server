-- ====================================================================
-- V202610152100__add_customer_role.sql
-- 대상 테이블: customer (소유 P6 · 작업 P1)
--
-- 주의: Flyway 적용 후에는 이 파일을 수정하지 마십시오 (체크섬 불변).
--       변경은 새 V 파일에 ALTER 로 작성합니다.
--
-- 관리자 인증의 저장 모델을 고객 테이블에 둔다(PH-49a-①, #561).
-- role 은 계정 구분이고, 그 관리자가 열 수 있는 권한은 admin_permission 에 둔다
-- (V202610152102).
--
-- 관리자를 별도 테이블로 분리하지 않는 이유는 로그인 경로와 비밀번호 정책을
-- 고객과 공유하기 때문이다. 관리자 로그인도 SessionLoginManager 를 그대로 쓴다.
--
-- NOT NULL DEFAULT 'CUSTOMER' 로 추가하므로 기존 고객 행은 CUSTOMER 로 채워지고,
-- 컬럼을 나열하는 시드·테스트 INSERT 도 수정 없이 CUSTOMER 가 들어간다.
--
-- MySQL 은 DDL 이 암묵적 커밋이라 실패해도 롤백되지 않으므로, 한 파일이
-- 다루는 테이블을 customer 하나로 제한한다. 표시명 공통코드는 V202610152101,
-- 권한 테이블은 V202610152102 에 따로 둔다.
-- ====================================================================

ALTER TABLE customer
    ADD COLUMN role VARCHAR(12) NOT NULL DEFAULT 'CUSTOMER'
        COMMENT '계정 구분. CUSTOMER / ADMIN'
        AFTER status,
    ADD CONSTRAINT ck_customer_role
        CHECK (role IN ('CUSTOMER', 'ADMIN'));
