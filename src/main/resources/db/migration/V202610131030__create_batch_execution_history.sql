-- ====================================================================
-- V202610131030__create_batch_execution_history.sql
-- COB 등 배치 스텝 실행 기록 (P5, #603)
--
-- CobRunnerService가 스텝을 하나 실행할 때마다 결과를 1행 추가한다
-- (append-only, UPDATE·DELETE 없음). 관리자 조회 API는 후속 이슈.
-- ====================================================================

CREATE TABLE batch_execution_history
(
    batch_execution_history_id BIGINT       NOT NULL AUTO_INCREMENT,
    job_name                   VARCHAR(50)  NOT NULL,
    step_name                  VARCHAR(100) NOT NULL,
    business_date               DATE        NOT NULL,
    started_at                  DATETIME(6) NOT NULL,
    ended_at                    DATETIME(6) NOT NULL,
    status                      VARCHAR(10) NOT NULL,
    failure_reason               TEXT NULL,
    PRIMARY KEY (batch_execution_history_id)
) ENGINE=InnoDB COMMENT='COB 등 배치 스텝 실행 기록 (#603)';
