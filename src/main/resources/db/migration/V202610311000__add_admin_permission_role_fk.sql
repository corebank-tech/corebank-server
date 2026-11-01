-- ====================================================================
-- V202610311000__add_admin_permission_role_fk.sql
-- 대상 테이블: admin_permission (소유 P1), customer (소유 P6, UNIQUE 추가만)
--
-- 주의: Flyway 적용 후에는 이 파일을 수정하지 마십시오 (체크섬 불변).
--       변경은 새 V 파일에 ALTER 로 작성합니다.
--
-- role=CUSTOMER 인 고객에 권한 행이 붙거나, 권한 행이 남은 관리자가 CUSTOMER 로
-- 내려가는 모순을 DB 가 막는다(PH-49b, #812). customer 와 admin_permission 두 테이블에 걸려
-- 단일 CHECK 로는 못 막아, admin_permission 에 role 을 두고 (customer_id, role) 로
-- customer 를 참조한다(PR #572 리뷰 제안). 새 FK 가 customer_id 참조를 겸하므로
-- 기존 fk_admin_permission_customer 는 지운다.
-- ====================================================================

ALTER TABLE customer
    ADD CONSTRAINT uk_customer_id_role UNIQUE (customer_id, role);

ALTER TABLE admin_permission
    ADD COLUMN role VARCHAR(12) NOT NULL DEFAULT 'ADMIN'
        COMMENT '권한을 가질 수 있는 계정 구분. 항상 ADMIN' AFTER customer_id,
    ADD CONSTRAINT ck_admin_permission_role CHECK (role = 'ADMIN'),
    ADD CONSTRAINT fk_admin_permission_admin
        FOREIGN KEY (customer_id, role) REFERENCES customer (customer_id, role);

-- FK 추가와 삭제를 한 문장에 섞으면 MySQL 이 거부할 수 있어 나눠 실행한다.
ALTER TABLE admin_permission
    DROP FOREIGN KEY fk_admin_permission_customer;
