-- 정보계 ETL 계정 권한 분리(P3 설계 §3-4 원칙, 스키마·계정 이름은 P5가 정함). 배포 때 한 번 실행하고 CHANGE_ME는 그때 바꾼다.
-- 레플리카(minicore): ETL 계정은 적재가 읽는 테이블에만 SELECT를 준다. customer 같은 개인정보 테이블은 주지 않는다.
CREATE USER IF NOT EXISTS 'etl_replica_reader'@'%' IDENTIFIED BY 'CHANGE_ME';
GRANT SELECT ON minicore.ledger_entry TO 'etl_replica_reader'@'%';
GRANT SELECT ON minicore.account TO 'etl_replica_reader'@'%';
GRANT SELECT ON minicore.product TO 'etl_replica_reader'@'%';
GRANT SELECT ON minicore.product_subscription TO 'etl_replica_reader'@'%';
GRANT SELECT ON minicore.gl_journal_entry TO 'etl_replica_reader'@'%';
GRANT SELECT ON minicore.batch_execution_history TO 'etl_replica_reader'@'%';
GRANT SELECT ON minicore.holiday TO 'etl_replica_reader'@'%';
-- 마트(info_mart): 쓰기는 ETL 계정만, 정보계 API 계정은 읽기만. 둘 다 DDL 권한은 없다.
CREATE USER IF NOT EXISTS 'etl_mart_writer'@'%' IDENTIFIED BY 'CHANGE_ME';
GRANT SELECT, INSERT, UPDATE, DELETE ON info_mart.* TO 'etl_mart_writer'@'%';
CREATE USER IF NOT EXISTS 'info_api_reader'@'%' IDENTIFIED BY 'CHANGE_ME';
GRANT SELECT ON info_mart.* TO 'info_api_reader'@'%';
