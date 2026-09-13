-- ====================================================================
-- V202609132350__extend_ledger_partitions_to_2027_12.sql
-- P4
--
-- ledger_entry 월별 파티션을 2027-12까지 선반영한다 (#379).
--
-- 기존 파티션은 p202607~p202612 + pmax 뿐이라 2027-01-01부터는 모든 원장 행이
-- pmax 한 칸에 쌓인다. 그러면 ix_le_account_time 기반 기간 조회의 파티션 프루닝이
-- 무력화된다. REORGANIZE PARTITION은 pmax에 든 데이터를 새 칸으로 옮기는 작업이라
-- pmax가 비어 있는 지금(2026-09)이 가장 싸다.
--
-- add_ledger_partition은 같은 이름의 파티션이 이미 있으면 아무 일도 하지 않으므로
-- 수동으로 미리 만들어 둔 환경에서도 안전하다(V202608010980 참고).
-- ====================================================================
CALL add_ledger_partition('2027-01-01');
CALL add_ledger_partition('2027-02-01');
CALL add_ledger_partition('2027-03-01');
CALL add_ledger_partition('2027-04-01');
CALL add_ledger_partition('2027-05-01');
CALL add_ledger_partition('2027-06-01');
CALL add_ledger_partition('2027-07-01');
CALL add_ledger_partition('2027-08-01');
CALL add_ledger_partition('2027-09-01');
CALL add_ledger_partition('2027-10-01');
CALL add_ledger_partition('2027-11-01');
CALL add_ledger_partition('2027-12-01');
