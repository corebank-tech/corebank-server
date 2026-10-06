-- ====================================================================
-- V202610061129__add_customer_role_and_permissions.sql
-- 대상 테이블: customer (소유 P6 · 작업 P1)
--
-- 관리자 인증의 저장 모델을 고객 테이블에 둔다(PH-49a-①, #561).
-- role 은 계정 구분이고 permissions 는 그 관리자가 열 수 있는 문의 목록이다.
-- 관리자를 별도 테이블로 분리하지 않는 이유는 로그인 경로와 비밀번호 정책을
-- 고객과 공유하기 때문이다. 관리자 로그인도 SessionLoginManager 를 그대로 쓴다.
--
-- permissions 는 고정 5종(GL_READ · GL_WRITE · CUSTOMER_READ · CUSTOMER_WRITE ·
-- AUDIT_READ)을 쉼표로 이어 붙인 CSV 다. 권한이 5개뿐이고 부여·회수가 관리자
-- 계정 단위로만 일어나 조인 테이블을 두지 않는다. 부여·회수 API 는 PH-49b 에서
-- 만들고 이 시점에는 시드로만 넣는다.
--
-- role=CUSTOMER 인 행의 permissions 는 NULL 이다. 빈 문자열로 두면 "권한이
-- 0개인 관리자"와 "관리자가 아닌 고객"을 구분할 수 없어 NULL 을 허용한다.
--
-- NOT NULL DEFAULT 'CUSTOMER' 로 추가하므로 기존 고객 행은 CUSTOMER 로 채워지고,
-- 컬럼을 나열하는 시드·테스트 INSERT 도 수정 없이 CUSTOMER 가 들어간다.
--
-- MySQL 은 DDL 이 암묵적 커밋이라 실패해도 롤백되지 않으므로, 한 파일이
-- 다루는 테이블을 customer 하나로 제한한다. 표시명 공통코드는
-- V202610061130 에 따로 둔다.
-- ====================================================================

ALTER TABLE customer
    ADD COLUMN role VARCHAR(12) NOT NULL DEFAULT 'CUSTOMER'
        COMMENT '계정 구분. CUSTOMER / ADMIN'
        AFTER status,
    ADD COLUMN permissions VARCHAR(200) NULL
        COMMENT '관리자 권한 CSV. 예: GL_READ,CUSTOMER_READ. 고객은 NULL'
        AFTER role,
    ADD CONSTRAINT ck_customer_role
        CHECK (role IN ('CUSTOMER', 'ADMIN'));
