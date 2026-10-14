-- ====================================================================
-- V202610152102__create_admin_permission.sql
-- 대상 테이블: admin_permission (신규, 소유 P1)
--
-- 주의: Flyway 적용 후에는 이 파일을 수정하지 마십시오 (체크섬 불변).
--       변경은 새 V 파일에 ALTER 로 작성합니다.
--
-- 관리자 권한을 행 하나에 하나씩 둔다(PH-49a-①, #561).
-- customer.permissions 에 CSV 로 담으려다 조인 테이블로 바꿨다(PR #572 리뷰) —
-- CSV 는 글자일 뿐이라 'GL_RAED' 같은 오타를 DB 가 막지 못한다.
-- 권한이 없는 관리자는 행이 0개이고, 관리자인지 아닌지는 customer.role 로 갈린다.
--
-- common_code 에 FK 를 걸지 못한다. 그 테이블이 code_group 으로 여러 종류를 함께 담아
-- ADMIN_PERMISSION 행만 참조할 수 없다. 대신 CHECK 로 값을 고정하고 둘을 함께 바꾼다.
-- CHECK 에 COLLATE 를 지정하는 것은 PH-49b 부여 API 가 요청 문자열을 그대로 넣을 때
-- 'gl_read' 가 통과하는 것을 막기 위해서다.
-- ====================================================================

CREATE TABLE admin_permission (
    customer_id BIGINT      NOT NULL,
    permission  VARCHAR(20) NOT NULL COMMENT 'GL_READ / GL_WRITE / CUSTOMER_READ / CUSTOMER_WRITE / AUDIT_READ',
    granted_at  DATETIME(6) NOT NULL COMMENT '권한을 준 시각. 회수는 행 삭제라 이력은 audit_log 가 맡는다',
    PRIMARY KEY (customer_id, permission),
    CONSTRAINT fk_admin_permission_customer
        FOREIGN KEY (customer_id) REFERENCES customer (customer_id),
    CONSTRAINT ck_admin_permission
        CHECK (permission COLLATE utf8mb4_0900_as_cs IN (
            'GL_READ', 'GL_WRITE', 'CUSTOMER_READ', 'CUSTOMER_WRITE', 'AUDIT_READ'
        ))
) ENGINE=InnoDB COMMENT='관리자 권한 (#561)';
