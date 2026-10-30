ALTER TABLE account
    DROP CHECK ck_account_status;

ALTER TABLE account
    ADD CONSTRAINT ck_account_status
        CHECK (
            status IN (
                'ACTIVE',
                'SUSPENDED',
                'MATURED',
                'CLOSED',
                'DORMANT'
            )
        );
INSERT INTO common_code (code_group, code, code_name, sort_order, created_at, updated_at)
VALUES ('ACCOUNT_STATUS', 'MATURED', '만기 도달', 4, NOW(6), NOW(6)),
       ('ACCOUNT_STATUS', 'DORMANT', '휴면', 5, NOW(6), NOW(6));
