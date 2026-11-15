-- P6 성공 이체와 FE 행동 흐름의 일대일 연결. 후속 TransferSettled 리스너가 이 공개 부품을 사용한다.
CREATE TABLE fds_transfer_behavior_flow (
    ref_id BIGINT NOT NULL PRIMARY KEY,
    flow_id CHAR(36) NOT NULL,
    customer_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_fds_transfer_behavior_flow_flow UNIQUE (flow_id),
    CONSTRAINT fk_fds_transfer_behavior_flow_flow FOREIGN KEY (flow_id) REFERENCES fds_behavior_flow (flow_id)
) ENGINE=InnoDB;
