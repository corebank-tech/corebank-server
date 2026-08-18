-- ====================================================================
-- V202608181253__add_created_at_to_transfer_limit.sql
-- 이체한도 (P1)
--
-- BaseEntity(createdAt·updatedAt을 모두 nullable=false로 매핑) 상속을 위해
-- created_at 을 추가한다. 두 테이블 모두 생성 이후 적재된 행이 없어
-- NOT NULL 컬럼을 기본값 없이 바로 추가할 수 있다.
--
-- DEFAULT CURRENT_TIMESTAMP 는 쓰지 않는다 - 시각 주입은 DB가 아니라
-- JpaAuditingConfig 의 Clock.systemUTC() 가 담당한다.
--
-- 주의: Flyway 적용 후에는 이 파일을 수정하지 마십시오 (체크섬 불변).
--       변경은 새 V 파일에 ALTER 로 작성합니다.
-- ====================================================================

ALTER TABLE transfer_limit
    ADD COLUMN created_at DATETIME(6) NOT NULL AFTER version;

ALTER TABLE transfer_limit_daily_usage
    ADD COLUMN created_at DATETIME(6) NOT NULL AFTER used_amount;
