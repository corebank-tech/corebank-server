CREATE TABLE batch_execution_lock (
                                      job_name           VARCHAR(50)  NOT NULL,
                                      currently_running  BOOLEAN      NOT NULL DEFAULT FALSE,
                                      updated_at         DATETIME(6)  NOT NULL,
                                      PRIMARY KEY (job_name)
) COMMENT='배치 중복 트리거 방지 락 - Fineract SchedulerTriggerListener 패턴 참고(#189)';

INSERT INTO batch_execution_lock (job_name, currently_running, updated_at)
VALUES ('DAILY_TRANSFER_BATCH', FALSE, NOW());