-- ====================================================================
-- V202608181253__add_created_at_to_transfer_limit.sql
-- 이체한도 (P1)
--
-- BaseEntity(createdAt·updatedAt을 모두 nullable=false로 매핑) 상속을 위해
-- created_at 을 추가한다.
--
-- 두 테이블은 V202608010950 에서 이미 생성됐고 환경마다 적재 상태가 다를 수
-- 있으므로, 빈 테이블을 전제하지 않는다. NULL 로 추가해 기존 행을 updated_at
-- 값으로 채운 뒤 NOT NULL 로 조인다. 행이 없으면 UPDATE 가 0건으로 지나간다.
--
-- DEFAULT CURRENT_TIMESTAMP 는 쓰지 않는다 - 시각 주입은 DB가 아니라
-- JpaAuditingConfig 의 Clock.systemUTC() 가 담당한다.
--
-- 주의: Flyway 적용 후에는 이 파일을 수정하지 마십시오 (체크섬 불변).
--       변경은 새 V 파일에 ALTER 로 작성합니다.
-- ====================================================================

ALTER TABLE transfer_limit
    ADD COLUMN created_at DATETIME(6) NULL AFTER version;

UPDATE transfer_limit
SET created_at = updated_at
WHERE created_at IS NULL;

ALTER TABLE transfer_limit
    MODIFY COLUMN created_at DATETIME(6) NOT NULL;

ALTER TABLE transfer_limit_daily_usage
    ADD COLUMN created_at DATETIME(6) NULL AFTER used_amount;

UPDATE transfer_limit_daily_usage
SET created_at = updated_at
WHERE created_at IS NULL;

ALTER TABLE transfer_limit_daily_usage
    MODIFY COLUMN created_at DATETIME(6) NOT NULL;
