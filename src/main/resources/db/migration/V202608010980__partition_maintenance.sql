-- ====================================================================
-- ledger_entry 월별 파티션 추가 프로시저
-- pmax 파티션이 있어 당장은 동작하지만, 데이터가 pmax 로만 쌓이면
-- 파티션 프루닝 효과가 사라진다. 매월 1회 호출해 실 파티션을 만든다.
-- ====================================================================

DELIMITER $$

CREATE PROCEDURE add_ledger_partition(IN target_month DATE)
BEGIN
    DECLARE pname   VARCHAR(16);
    DECLARE pbound  VARCHAR(16);
    DECLARE cnt     INT;

    SET pname  = CONCAT('p', DATE_FORMAT(target_month, '%Y%m'));
    SET pbound = DATE_FORMAT(DATE_ADD(target_month, INTERVAL 1 MONTH), '%Y-%m-01');

    SELECT COUNT(*) INTO cnt
      FROM information_schema.PARTITIONS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME   = 'ledger_entry'
       AND PARTITION_NAME = pname;

    IF cnt = 0 THEN
        SET @sql = CONCAT(
            'ALTER TABLE ledger_entry REORGANIZE PARTITION pmax INTO (',
            'PARTITION ', pname, ' VALUES LESS THAN (''', pbound, '''), ',
            'PARTITION pmax VALUES LESS THAN (MAXVALUE))');
        PREPARE st FROM @sql;
        EXECUTE st;
        DEALLOCATE PREPARE st;
    END IF;
END$$

DELIMITER ;
