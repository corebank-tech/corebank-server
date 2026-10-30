-- ====================================================================
-- V202610281110__add_gl_journal_entry_trade_date_index.sql
-- P3 (GL-IDX)
--
-- 관리자 시산표(GET /admin/trial-balance)의 계정별 집계가 조회 기간만 읽도록 인덱스를 추가한다.
-- 인덱스가 없으면 기간과 무관하게 분개 전체를 fk_gl_journal_entry_account 순서로 읽고 줄마다 본 테이블을 다시 찾는다
-- (PH-28 베이스라인, 운영 647만 행 전 구간 평균 26.8초).
--
-- 컬럼 순서는 거래일이 먼저다. 집계 SQL 이 쓰는 컬럼(trade_date, account_code, dr_cr, amount)을 모두 담아 본 테이블을 읽지 않는다.
-- account_code 로 시작하는 인덱스는 넣지 않는다.
--   1. (account_code, trade_date) 는 기간 범위로 좁히지 못하고 본 테이블을 흩어 읽어 지금보다 느렸다(로컬 60b 비교, 약 2배).
--   2. fk_gl_journal_entry_account 는 FK 가 자동으로 만든 인덱스라, 같은 FK 를 받칠 수 있는 인덱스가 생기면 MySQL 이 조용히 지운다.
--
-- 기존 행이 많아 온라인 DDL 로 만든다. 지원되지 않으면 테이블을 잠그지 않고 실패한다.
--
-- 온라인 DDL 도 시작과 끝에 메타데이터 잠금이 잠깐 필요하다. 긴 트랜잭션이 이 테이블을 쓰고 있으면 기다리고,
-- 그동안 뒤에 오는 분개 쓰기(이체 기표)도 줄줄이 막힌다. 기본 대기 상한은 1년이라, 10초로 묶어 막히는 시간을 제한한다.
-- 10초 안에 잠금을 못 잡으면 이 마이그레이션이 실패한다. MySQL 8 DDL 은 원자적이라 인덱스는 남지 않는다.
-- 그때는 flyway_schema_history 의 실패 행을 지우고(flyway repair) 긴 트랜잭션이 끝난 뒤 다시 배포한다.
-- ====================================================================
SET SESSION lock_wait_timeout = 10;

CREATE INDEX ix_gl_journal_entry_trade_date
    ON gl_journal_entry (trade_date, account_code, dr_cr, amount)
    ALGORITHM = INPLACE LOCK = NONE;

SET SESSION lock_wait_timeout = DEFAULT;
