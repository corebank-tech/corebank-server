-- =====================================================================
-- 공통코드 테이블 (REQ-CMN-023)
-- 관리: P5 (공통기반)
--
-- 코드값 -> 화면 표시명 매핑. 화면/서버에 문자열을 하드코딩하지 않기 위한 테이블.
-- code 값은 서버 Enum 값과 문자열이 정확히 일치해야 한다.
--
-- code_group  : 코드 그룹명
-- code        : 코드값. 서버 Enum 값과 일치
-- code_name   : 화면 표시 한글명
-- sort_order  : 드롭다운 표시 순서
-- use_yn      : 사용 여부. 물리 삭제 대신 N 처리
-- description : 관리자용 설명
-- created_at  : JPA Auditing
-- updated_at  : JPA Auditing
-- =====================================================================

CREATE TABLE common_code
(
    code_group  VARCHAR(50)  NOT NULL,
    code        VARCHAR(50)  NOT NULL,
    code_name   VARCHAR(100) NOT NULL,
    sort_order  INT          NOT NULL,
    use_yn      CHAR(1)      NOT NULL DEFAULT 'Y',
    description VARCHAR(200),
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (code_group, code)
);

CREATE INDEX ix_common_code_group ON common_code (code_group, use_yn, sort_order);


-- =====================================================================
-- 시드 데이터
-- created_at / updated_at 은 JPA Auditing 대상이나, 초기 적재는 SQL 이므로 직접 입력한다.
-- =====================================================================

-- 계좌 상태 (P2)
INSERT INTO common_code (code_group, code, code_name, sort_order, created_at, updated_at)
VALUES ('ACCOUNT_STATUS', 'ACTIVE', '정상', 1, NOW(6), NOW(6)),
       ('ACCOUNT_STATUS', 'SUSPENDED', '거래정지', 2, NOW(6), NOW(6)),
       ('ACCOUNT_STATUS', 'CLOSED', '해지', 3, NOW(6), NOW(6));

-- 계좌 유형 (P2)
INSERT INTO common_code (code_group, code, code_name, sort_order, created_at, updated_at)
VALUES ('ACCOUNT_TYPE', 'DEMAND_DEPOSIT', '입출금계좌', 1, NOW(6), NOW(6)),
       ('ACCOUNT_TYPE', 'TIME_DEPOSIT', '정기예금', 2, NOW(6), NOW(6)),
       ('ACCOUNT_TYPE', 'INSTALLMENT_SAVINGS', '정기적금', 3, NOW(6), NOW(6));

-- 계좌 화면 그룹 (P2) - ACCOUNT_TYPE 과 값 종류가 다름
INSERT INTO common_code (code_group, code, code_name, sort_order, description, created_at, updated_at)
VALUES ('ACCOUNT_GROUP', 'DEMAND_DEPOSIT', '입출금계좌', 1, '전체 계좌 조회 화면 그룹 제목', NOW(6), NOW(6)),
       ('ACCOUNT_GROUP', 'DEPOSIT_SAVINGS', '예금적금', 2, '전체 계좌 조회 화면 그룹 제목', NOW(6), NOW(6));

-- 거래 구분 (P2)
INSERT INTO common_code (code_group, code, code_name, sort_order, created_at, updated_at)
VALUES ('TRANSACTION_DIRECTION', 'DEPOSIT', '입금', 1, NOW(6), NOW(6)),
       ('TRANSACTION_DIRECTION', 'WITHDRAWAL', '출금', 2, NOW(6), NOW(6));

-- 상품 구분 (P3)
INSERT INTO common_code (code_group, code, code_name, sort_order, created_at, updated_at)
VALUES ('PRODUCT_GROUP', 'DEPOSIT', '정기예금', 1, NOW(6), NOW(6)),
       ('PRODUCT_GROUP', 'SAVINGS', '정기적금', 2, NOW(6), NOW(6));

-- 상품 판매 상태 (P3)
INSERT INTO common_code (code_group, code, code_name, sort_order, created_at, updated_at)
VALUES ('PRODUCT_SALE_STATUS', 'ON_SALE', '판매중', 1, NOW(6), NOW(6)),
       ('PRODUCT_SALE_STATUS', 'SUSPENDED', '판매중지', 2, NOW(6), NOW(6));

-- 거래 처리결과 (공용) - 즉시이체 / 자동이체 회차 / 상품가입 결과가 공유
INSERT INTO common_code (code_group, code, code_name, sort_order, description, created_at, updated_at)
VALUES ('PROCESS_RESULT_STATUS', 'SUCCESS', '성공', 1, NULL, NOW(6), NOW(6)),
       ('PROCESS_RESULT_STATUS', 'ERROR', '실패', 2, NULL, NOW(6), NOW(6)),
       ('PROCESS_RESULT_STATUS', 'PROCESSING', '처리중', 3, '응답 유실 또는 타임아웃으로 결과 미확정', NOW(6), NOW(6));

-- 이체 유형 (P4)
INSERT INTO common_code (code_group, code, code_name, sort_order, created_at, updated_at)
VALUES ('TRANSFER_TYPE', 'IMMEDIATE', '즉시이체', 1, NOW(6), NOW(6)),
       ('TRANSFER_TYPE', 'SCHEDULED', '예약이체', 2, NOW(6), NOW(6)),
       ('TRANSFER_TYPE', 'AUTO', '자동이체', 3, NOW(6), NOW(6));

-- 예약이체 상태 (P3)
INSERT INTO common_code (code_group, code, code_name, sort_order, created_at, updated_at)
VALUES ('SCHEDULED_TRANSFER_STATUS', 'WAITING', '대기', 1, NOW(6), NOW(6)),
       ('SCHEDULED_TRANSFER_STATUS', 'PROCESSING', '처리중', 2, NOW(6), NOW(6)),
       ('SCHEDULED_TRANSFER_STATUS', 'SUCCESS', '성공', 3, NOW(6), NOW(6)),
       ('SCHEDULED_TRANSFER_STATUS', 'FAILED', '실패', 4, NOW(6), NOW(6)),
       ('SCHEDULED_TRANSFER_STATUS', 'CANCELED', '취소', 5, NOW(6), NOW(6));

-- 자동이체 상태 (P5)
INSERT INTO common_code (code_group, code, code_name, sort_order, description, created_at, updated_at)
VALUES ('AUTO_TRANSFER_STATUS', 'NORMAL', '정상', 1, NULL, NOW(6), NOW(6)),
       ('AUTO_TRANSFER_STATUS', 'EXPIRED', '종료', 2, '이체 종료일 경과로 시스템 자동 전환', NOW(6), NOW(6)),
       ('AUTO_TRANSFER_STATUS', 'TERMINATED', '해지', 3, '고객이 직접 해지', NOW(6), NOW(6));

-- 알림 구분 (P6)
INSERT INTO common_code (code_group, code, code_name, sort_order, created_at, updated_at)
VALUES ('NOTIFICATION_TYPE', 'TRANSFER', '즉시이체 결과', 1, NOW(6), NOW(6)),
       ('NOTIFICATION_TYPE', 'SCHEDULED_TRANSFER', '예약이체 결과', 2, NOW(6), NOW(6)),
       ('NOTIFICATION_TYPE', 'AUTO_TRANSFER', '자동이체 결과', 3, NOW(6), NOW(6)),
       ('NOTIFICATION_TYPE', 'PRODUCT_SUBSCRIPTION', '상품가입 결과', 4, NOW(6), NOW(6));

-- 알림 읽음 상태 (P6)
INSERT INTO common_code (code_group, code, code_name, sort_order, created_at, updated_at)
VALUES ('NOTIFICATION_READ_STATUS', 'UNREAD', '안읽음', 1, NOW(6), NOW(6)),
       ('NOTIFICATION_READ_STATUS', 'READ', '읽음', 2, NOW(6), NOW(6));

-- 약관 구분 (P6) - 세부 값 미확정. P6 작성 후 주석 해제
-- INSERT INTO common_code (code_group, code, code_name, sort_order, created_at, updated_at) VALUES
-- ('TERMS_TYPE', '?', '?', 1, NOW(6), NOW(6));