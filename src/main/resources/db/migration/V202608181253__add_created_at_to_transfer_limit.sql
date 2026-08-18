-- ====================================================================
-- V202608181253__add_created_at_to_transfer_limit.sql
-- 이체한도 (P1)
--
-- BaseEntity(createdAt·updatedAt을 모두 nullable=false로 매핑) 상속을 위해
-- transfer_limit 에 created_at 을 추가한다.
--
-- 이 테이블은 V202608010950 에서 이미 생성됐고 환경마다 적재 상태가 다를 수
-- 있으므로, 빈 테이블을 전제하지 않는다. NULL 로 추가해 기존 행을 updated_at
-- 값으로 채운 뒤 NOT NULL 로 조인다. 행이 없으면 UPDATE 가 0건으로 지나간다.
--
-- DEFAULT CURRENT_TIMESTAMP 는 쓰지 않는다 - 시각 주입은 DB가 아니라
-- JpaAuditingConfig 의 Clock.systemUTC() 가 담당한다.
--
-- transfer_limit_daily_usage 의 같은 변경은 V202608181254 로 분리했다.
-- MySQL 은 DDL 이 암묵적 커밋이라 실패해도 롤백되지 않으므로, 한 파일이
-- 다루는 테이블을 하나로 제한해 부분 적용 시 복구 범위를 좁힌다.
-- ====================================================================

ALTER TABLE transfer_limit
    ADD COLUMN created_at DATETIME(6) NULL AFTER version;

UPDATE transfer_limit
SET created_at = updated_at
WHERE created_at IS NULL;

ALTER TABLE transfer_limit
    MODIFY COLUMN created_at DATETIME(6) NOT NULL;
