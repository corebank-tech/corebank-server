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
-- 걸친 일회성 타임존 컷오버이고, 테이블별로 20여 개 파일로 쪼개 순차
-- 적용하면 그 사이(첫 파일 적용~마지막 파일 적용 사이) DB가 절반은
-- KST로 보정되고 절반은 UTC로 남은 상태로 관측된다. 이는 규칙이 원래
-- 막으려는 "부분 실패로 인한 불일치"보다 더 큰 불일치를 만들어낸다.
--
-- 대상 판별: information_schema.COLUMNS에서 DATA_TYPE='datetime'인
-- 컬럼을 전부 순회해 +9시간(INTERVAL 9 HOUR) 보정한다. DATE 타입
-- (birth_date 등)은 대상에서 자연히 제외된다. flyway_schema_history는
-- Flyway 내부 테이블이라 명시적으로 제외한다.
--
-- 주의(ledger_entry): occurred_at은 PK이자 RANGE 파티션 키다. UPDATE로
-- 값이 바뀌면 MySQL이 필요 시 파티션 간 행 이동을 수행한다. 이 마이그레이션
-- 시점의 데이터 규모(데모/개발 데이터)에서는 허용 가능한 비용으로 판단했다.
-- ====================================================================

DELIMITER $$

CREATE PROCEDURE shift_datetime_columns_to_kst()
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE v_table_name VARCHAR(64);
    DECLARE v_column_name VARCHAR(64);
    DECLARE cur CURSOR FOR
        SELECT TABLE_NAME, COLUMN_NAME
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND DATA_TYPE = 'datetime'
          AND TABLE_NAME <> 'flyway_schema_history';
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

    OPEN cur;

    read_loop: LOOP
        FETCH cur INTO v_table_name, v_column_name;
        IF done THEN
            LEAVE read_loop;
        END IF;

        SET @sql = CONCAT(
            'UPDATE `', v_table_name, '` ',
            'SET `', v_column_name, '` = `', v_column_name, '` + INTERVAL 9 HOUR ',
            'WHERE `', v_column_name, '` IS NOT NULL'
        );
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END LOOP;

    CLOSE cur;
END$$

DELIMITER ;

CALL shift_datetime_columns_to_kst();

DROP PROCEDURE shift_datetime_columns_to_kst;
