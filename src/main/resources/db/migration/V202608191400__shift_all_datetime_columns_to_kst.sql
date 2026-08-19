-- ====================================================================
-- V202608191400__shift_all_datetime_columns_to_kst.sql
-- 전 테이블 DATETIME(6) 컬럼의 기존 데이터를 UTC → KST로 일괄 보정 (#179)
--
-- 배경: 2026-08-19 "DB 저장 및 JVM 시스템 시간을 KST로 통일하고 UTC↔KST
-- 변환 과정을 없앤다"는 정책으로 전환했다(#179). 애플리케이션 설정
-- (Dockerfile, Hibernate, JDBC, JpaAuditingConfig)은 컷오버 시점부터
-- KST로 새 값을 쓰기 시작하지만, 이미 저장된 기존 행은 UTC 값 그대로다.
-- 그대로 두면 컷오버 이후 같은 컬럼 안에 UTC 값과 KST 값이 섞여
-- 9시간 어긋난 채로 공존한다.
--
-- 규칙 위반에 대한 의도적 예외 (팀 확인 완료, #179): flyway_guide.md
-- 규칙 ③(자기 도메인 파일만 작성)·⑤(한 파일 한 테이블)를 이 마이그레이션은
-- 의도적으로 어긴다. 이건 특정 도메인의 기능 변경이 아니라 DB 전체에
-- 걸친 일회성 타임존 컷오버이고, 테이블별로 쪼개 순차 적용하면 그 사이
-- DB가 일부는 KST로 보정되고 일부는 UTC로 남은 상태로 관측된다.
--
-- PR #191 리뷰 반영: 최초 버전은 information_schema 커서를 순회하는
-- CREATE PROCEDURE 방식이었으나, MySQL에서 CREATE/DROP PROCEDURE는 DDL로
-- 분류되어 실행 시점에 암시적 커밋을 일으킨다("Statements That Cause an
-- Implicit Commit"). 그러면 CALL 도중 일부 테이블만 처리한 채 실패해도
-- 이미 처리된 테이블은 롤백되지 않아, 정작 막으려던 "일부는 KST·일부는
-- UTC" 상태를 방지하지 못한다. 그래서 CREATE PROCEDURE 없이 순수 DML
-- (UPDATE)만으로 다시 작성했다 — DDL이 없으므로 Flyway가 이 스크립트
-- 전체를 하나의 트랜잭션으로 실행해 실패 시 전체 롤백이 가능하다.
-- 대상 컬럼 목록은 전 마이그레이션 적용 후 information_schema.COLUMNS를
-- 조회해 확정했다(DATA_TYPE = 'datetime', flyway_schema_history 제외).
--
-- 주의(ledger_entry): 이 테이블은 COMMENT에 "APPEND-ONLY. UPDATE/DELETE
-- 금지"라고 명시돼 있어 occurred_at에 대한 UPDATE는 원래 이 테이블의
-- 불변식을 어긴다. 아직 실제 서비스 운영 전(데이터 없음) 단계라 이번
-- 1회 타임존 컷오버에 한해 예외로 포함하기로 팀에서 확인했다(#179).
-- 서비스 운영 개시 이후에는 이런 일괄 UPDATE를 다시 쓸 수 없다.
--
-- 주의(ledger_entry 파티션): occurred_at은 RANGE COLUMNS 파티션 키다.
-- 이 시점 데이터는 월별 파티션(p202607~p202612)이 이미 다 만들어져
-- 있어(V202608010930), +9시간 보정으로 인한 파티션 간 이동은 같은
-- 테이블 안에서 안전하게 처리된다. pmax(MAXVALUE) 캐치올은 2027년
-- 이후 데이터에만 관여한다.
-- ====================================================================

UPDATE account SET withdrawal_registered_at = withdrawal_registered_at + INTERVAL 9 HOUR WHERE withdrawal_registered_at IS NOT NULL;
UPDATE account SET opened_date = opened_date + INTERVAL 9 HOUR WHERE opened_date IS NOT NULL;
UPDATE account SET closed_date = closed_date + INTERVAL 9 HOUR WHERE closed_date IS NOT NULL;
UPDATE account SET last_transaction_at = last_transaction_at + INTERVAL 9 HOUR WHERE last_transaction_at IS NOT NULL;
UPDATE account SET created_at = created_at + INTERVAL 9 HOUR WHERE created_at IS NOT NULL;
UPDATE account SET updated_at = updated_at + INTERVAL 9 HOUR WHERE updated_at IS NOT NULL;

UPDATE account_number_sequence SET created_at = created_at + INTERVAL 9 HOUR WHERE created_at IS NOT NULL;
UPDATE account_number_sequence SET updated_at = updated_at + INTERVAL 9 HOUR WHERE updated_at IS NOT NULL;

UPDATE audit_log SET requested_at = requested_at + INTERVAL 9 HOUR WHERE requested_at IS NOT NULL;

UPDATE auto_transfer SET registered_at = registered_at + INTERVAL 9 HOUR WHERE registered_at IS NOT NULL;
UPDATE auto_transfer SET terminated_at = terminated_at + INTERVAL 9 HOUR WHERE terminated_at IS NOT NULL;
UPDATE auto_transfer SET updated_at = updated_at + INTERVAL 9 HOUR WHERE updated_at IS NOT NULL;

UPDATE auto_transfer_execution SET executed_at = executed_at + INTERVAL 9 HOUR WHERE executed_at IS NOT NULL;

UPDATE common_code SET created_at = created_at + INTERVAL 9 HOUR WHERE created_at IS NOT NULL;
UPDATE common_code SET updated_at = updated_at + INTERVAL 9 HOUR WHERE updated_at IS NOT NULL;

UPDATE customer SET last_login_at = last_login_at + INTERVAL 9 HOUR WHERE last_login_at IS NOT NULL;
UPDATE customer SET previous_login_at = previous_login_at + INTERVAL 9 HOUR WHERE previous_login_at IS NOT NULL;
UPDATE customer SET password_changed_at = password_changed_at + INTERVAL 9 HOUR WHERE password_changed_at IS NOT NULL;
UPDATE customer SET joined_at = joined_at + INTERVAL 9 HOUR WHERE joined_at IS NOT NULL;
UPDATE customer SET created_at = created_at + INTERVAL 9 HOUR WHERE created_at IS NOT NULL;
UPDATE customer SET updated_at = updated_at + INTERVAL 9 HOUR WHERE updated_at IS NOT NULL;

UPDATE customer_terms_agreement SET agreed_at = agreed_at + INTERVAL 9 HOUR WHERE agreed_at IS NOT NULL;

UPDATE favorite_account SET registered_at = registered_at + INTERVAL 9 HOUR WHERE registered_at IS NOT NULL;

UPDATE idempotency_key SET created_at = created_at + INTERVAL 9 HOUR WHERE created_at IS NOT NULL;
UPDATE idempotency_key SET expires_at = expires_at + INTERVAL 9 HOUR WHERE expires_at IS NOT NULL;

-- APPEND-ONLY 예외 (파일 상단 설명 참고)
UPDATE ledger_entry SET occurred_at = occurred_at + INTERVAL 9 HOUR WHERE occurred_at IS NOT NULL;

UPDATE notification SET read_at = read_at + INTERVAL 9 HOUR WHERE read_at IS NOT NULL;
UPDATE notification SET occurred_at = occurred_at + INTERVAL 9 HOUR WHERE occurred_at IS NOT NULL;

UPDATE product SET created_at = created_at + INTERVAL 9 HOUR WHERE created_at IS NOT NULL;
UPDATE product SET updated_at = updated_at + INTERVAL 9 HOUR WHERE updated_at IS NOT NULL;

UPDATE product_subscription SET subscribed_at = subscribed_at + INTERVAL 9 HOUR WHERE subscribed_at IS NOT NULL;

UPDATE scheduled_transfer SET registered_at = registered_at + INTERVAL 9 HOUR WHERE registered_at IS NOT NULL;
UPDATE scheduled_transfer SET executed_at = executed_at + INTERVAL 9 HOUR WHERE executed_at IS NOT NULL;
UPDATE scheduled_transfer SET canceled_at = canceled_at + INTERVAL 9 HOUR WHERE canceled_at IS NOT NULL;

UPDATE subscription_terms_agreement SET read_at = read_at + INTERVAL 9 HOUR WHERE read_at IS NOT NULL;
UPDATE subscription_terms_agreement SET agreed_at = agreed_at + INTERVAL 9 HOUR WHERE agreed_at IS NOT NULL;

UPDATE terms SET created_at = created_at + INTERVAL 9 HOUR WHERE created_at IS NOT NULL;
UPDATE terms SET updated_at = updated_at + INTERVAL 9 HOUR WHERE updated_at IS NOT NULL;

UPDATE transaction_sequence SET updated_at = updated_at + INTERVAL 9 HOUR WHERE updated_at IS NOT NULL;

UPDATE transfer SET transferred_at = transferred_at + INTERVAL 9 HOUR WHERE transferred_at IS NOT NULL;
UPDATE transfer SET created_at = created_at + INTERVAL 9 HOUR WHERE created_at IS NOT NULL;

UPDATE transfer_limit SET created_at = created_at + INTERVAL 9 HOUR WHERE created_at IS NOT NULL;
UPDATE transfer_limit SET updated_at = updated_at + INTERVAL 9 HOUR WHERE updated_at IS NOT NULL;

UPDATE transfer_limit_daily_usage SET created_at = created_at + INTERVAL 9 HOUR WHERE created_at IS NOT NULL;
UPDATE transfer_limit_daily_usage SET updated_at = updated_at + INTERVAL 9 HOUR WHERE updated_at IS NOT NULL;

UPDATE transfer_limit_history SET created_at = created_at + INTERVAL 9 HOUR WHERE created_at IS NOT NULL;
UPDATE transfer_limit_history SET updated_at = updated_at + INTERVAL 9 HOUR WHERE updated_at IS NOT NULL;

UPDATE verification_request SET verified_at = verified_at + INTERVAL 9 HOUR WHERE verified_at IS NOT NULL;
UPDATE verification_request SET expires_at = expires_at + INTERVAL 9 HOUR WHERE expires_at IS NOT NULL;
UPDATE verification_request SET created_at = created_at + INTERVAL 9 HOUR WHERE created_at IS NOT NULL;
