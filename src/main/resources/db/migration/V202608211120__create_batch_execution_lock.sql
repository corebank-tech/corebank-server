-- ====================================================================
-- V202608211120__create_batch_execution_lock.sql
-- 배치 중복 트리거 방지 락 (P5)
--
-- 여러 인스턴스/재시작 등으로 같은 배치(DAILY_TRANSFER_BATCH)가 동시에
-- 중복 트리거되는 것을 막는다. Fineract SchedulerTriggerListener의 veto
-- 패턴을 참고 - 배치 전체 동안 락을 들고 있지 않고, 짧은 트랜잭션 안에서
-- currently_running 플래그를 확인·갱신한 뒤 즉시 커밋해서 반납한다.
-- (#189)
-- ====================================================================

CREATE TABLE batch_execution_lock
(
    job_name          VARCHAR(50) NOT NULL,
    currently_running BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (job_name)
) ENGINE=InnoDB COMMENT='배치 중복 트리거 방지 락 - Fineract SchedulerTriggerListener 패턴 참고(#189)';

INSERT INTO batch_execution_lock (job_name, currently_running, updated_at)
VALUES ('DAILY_TRANSFER_BATCH', FALSE, NOW());