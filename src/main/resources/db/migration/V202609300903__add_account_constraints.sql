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
